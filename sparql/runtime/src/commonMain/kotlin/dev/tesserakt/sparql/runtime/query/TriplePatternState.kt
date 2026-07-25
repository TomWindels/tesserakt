package dev.tesserakt.sparql.runtime.query

import dev.tesserakt.rdf.types.EncodedQuad
import dev.tesserakt.sparql.QueryStatistics
import dev.tesserakt.sparql.runtime.collection.MappingArrayHint
import dev.tesserakt.sparql.runtime.collection.ReindexableMappingArray
import dev.tesserakt.sparql.runtime.evaluation.*
import dev.tesserakt.sparql.runtime.evaluation.context.QueryContext
import dev.tesserakt.sparql.runtime.evaluation.mapping.Mapping
import dev.tesserakt.sparql.runtime.evaluation.mapping.mappingOf
import dev.tesserakt.sparql.runtime.query.jointree.JoinTree
import dev.tesserakt.sparql.runtime.query.jointree.from
import dev.tesserakt.sparql.runtime.stream.*
import dev.tesserakt.sparql.types.TriplePattern
import dev.tesserakt.sparql.util.Cardinality
import kotlin.jvm.JvmInline
import kotlin.math.absoluteValue

sealed class TriplePatternState<P : TriplePatternState.Predicate>(
    val context: QueryContext,
    val s: Subject,
    val p: P,
    val o: Object
) : MutableJoinState {

    /* the various elements making up a single triple pattern, with binding names and quad elements encoded */

    sealed interface Subject

    sealed interface Predicate

    sealed interface Object

    /**
     * Subset of predicates: those that can function (peek functionality) w/o maintaining state
     */
    sealed interface StatelessPredicate : Predicate

    /**
     * Subset of predicates: those that are guaranteed to not contain any bindings
     */
    sealed interface UnboundPredicate : Predicate

    sealed interface RepeatingPredicate : UnboundPredicate {
        val element: UnboundPredicate
    }

    /**
     * Binding name, encoded by the corresponding [QueryContext]
     */
    @JvmInline
    value class Binding(val id: BindingIdentifier) : Subject, Predicate, Object {
        override fun toString() = id.toString()
    }

    /**
     * Quad element, encoded by the corresponding [QueryContext]
     */
    @JvmInline
    value class Exact(val id: TermIdentifier) : Subject, UnboundPredicate, StatelessPredicate, Object {
        override fun toString() = id.toString()
    }

    @JvmInline
    value class Negated(val terms: SimpleAlts) : UnboundPredicate, StatelessPredicate {
        override fun toString() = "!${terms}"
    }

    @JvmInline
    value class Alts(val allowed: List<UnboundPredicate>) : UnboundPredicate {
        override fun toString() = allowed.joinToString(
            separator = " | ",
            prefix = "(",
            postfix = ")",
            transform = { "($it)" }
        )
    }

    @JvmInline
    value class SimpleAlts(val allowed: List<StatelessPredicate>) : UnboundPredicate, StatelessPredicate {
        override fun toString() = allowed.joinToString(
            separator = " | ",
            prefix = "(",
            postfix = ")",
            transform = { "($it)" }
        )
    }

    /*
    cannot always be destructured using generated bindings, as they sometimes appear in repeating or inverse structures;
    in the repeating cases, bindings are not allowed, so this version cannot be used
    */
    @JvmInline
    value class Sequence(val chain: List<Predicate>) : Predicate {
        override fun toString() = chain.joinToString(
            separator = " / ",
            prefix = "(",
            postfix = ")",
            transform = { "($it)" }
        )
    }

    /*
    cannot always be destructured using generated bindings, as they sometimes appear in repeating or inverse structures
    */
    @JvmInline
    value class UnboundSequence(val chain: List<UnboundPredicate>) : UnboundPredicate {
        override fun toString() = chain.joinToString(" / ")
    }

    @JvmInline
    value class ZeroOrMore(override val element: UnboundPredicate) : RepeatingPredicate, UnboundPredicate {
        override fun toString() = "($element)*"
    }

    @JvmInline
    value class OneOrMore(override val element: UnboundPredicate) : RepeatingPredicate, UnboundPredicate {
        override fun toString() = "($element)+"
    }

    /* the various triple pattern types; with implementation (behaviour) based on the exact predicate type */

    sealed class ArrayBackedPatternState<P : Predicate>(
        context: QueryContext,
        subj: Subject,
        pred: P,
        obj: Object
    ) : TriplePatternState<P>(context, subj, pred, obj) {

        /**
         * The backing structure used to store all intermediate matches with this specific triple pattern instance.
         */
        open val data = ReindexableMappingArray(bindingIdentifierSetOf(subj, pred, obj))

        override val cardinality get() = data.cardinality

        override var changeCount = 0L

        final override fun process(delta: DataDelta) {
            when (delta) {
                is DataAddition -> {
                    val new = peek(delta)
                    changeCount += data.addAll(new)
                }

                is DataDeletion -> {
                    val removed = peek(delta)
                    changeCount += data.removeAll(removed)
                }
            }
        }

        final override fun join(delta: MappingDelta): Stream<MappingDelta> {
            val removed = (delta.origin as? DataDeletion)?.value
            return if (removed != null) {
                val ignored = peek(removed)
                delta.mapToStream {
                    data
                        .iter(delta.value)
                        .remove(ignored)
                        .join(delta.value)
                }
            } else {
                delta.mapToStream { data.join(delta.value) }
            }
        }

        final override fun reindex(bindings: BindingIdentifierSet, hint: MappingArrayHint) {
            data.reindex(bindings, hint)
        }

        // as these are "stateless" compared to prior data, the operation type associated with the delta is irrelevant

        final override fun peek(delta: DataAddition): Stream<Mapping> {
            return peek(delta.value)
        }

        abstract fun peek(quad: EncodedQuad): Stream<Mapping>

    }

    class ExactPatternState(
        context: QueryContext,
        subj: Subject,
        val pred: Exact,
        obj: Object
    ) : ArrayBackedPatternState<Exact>(context, subj, pred, obj) {

        override fun peek(quad: EncodedQuad): Stream<Mapping> {
            if (pred.id.id != quad.p) {
                return emptyStream()
            }
            val s = subjectMappingOrNull(quad) ?: return emptyStream()
            val o = objectMappingOrNull(quad) ?: return emptyStream()
            val result = s.join(o) ?: return emptyStream()
            return streamOf(result)
        }

    }

    class BindingPatternState(
        context: QueryContext,
        subj: Subject,
        pred: Binding,
        obj: Object
    ) : ArrayBackedPatternState<Binding>(context, subj, pred, obj) {

        override fun peek(quad: EncodedQuad): Stream<Mapping> {
            val s = subjectMappingOrNull(quad) ?: return emptyStream()
            val o = objectMappingOrNull(quad) ?: return emptyStream()
            val p = mappingOf(context, p.id to TermIdentifier(quad.p))
            val result = s.join(p)?.join(o) ?: return emptyStream()
            return streamOf(result)
        }

    }

    class NegatedPatternState(
        context: QueryContext,
        subj: Subject,
        val pred: Negated,
        obj: Object
    ) : ArrayBackedPatternState<Negated>(context, subj, pred, obj) {

        override fun peek(quad: EncodedQuad): Stream<Mapping> {
            if (!pred.matches(quad.p)) {
                return emptyStream()
            }
            val s = subjectMappingOrNull(quad) ?: return emptyStream()
            val o = objectMappingOrNull(quad) ?: return emptyStream()
            val result = s.join(o) ?: return emptyStream()
            return streamOf(result)
        }

    }

    class RepeatingPatternState(
        context: QueryContext,
        subj: Subject,
        pred: RepeatingPredicate,
        obj: Object
    ) : TriplePatternState<RepeatingPredicate>(context, subj, pred, obj) {

        private val state = when (pred) {
            is ZeroOrMore -> RepeatingPathState.zeroOrMore(
                context = context,
                start = subj,
                predicate = pred,
                end = obj
            )

            is OneOrMore -> RepeatingPathState.oneOrMore(
                context = context,
                start = subj,
                predicate = pred,
                end = obj
            )
        }

        // TODO: use the inner state to increment the count every time a triple became part of the segment list
        override val changeCount: Long
            get() = -1

        override val cardinality: Cardinality
            get() = state.cardinality

        override fun process(delta: DataDelta) {
            state.process(delta)
        }

        override fun peek(delta: DataAddition): Stream<Mapping> {
            return state.peek(delta)
        }

        override fun peek(delta: DataDeletion): Stream<Mapping> {
            return state.peek(delta)
        }

        override fun join(delta: MappingDelta): Stream<MappingDelta> {
            val removed = delta.origin as? DataDeletion
            return if (removed != null) {
                val ignored = peek(removed)
                delta.mapToStream { state.join(streamOf(delta.value), ignore = ignored) }
            } else {
                delta.mapToStream { state.join(streamOf(delta.value)) }
            }
        }

        override fun reindex(bindings: BindingIdentifierSet, hint: MappingArrayHint) {
            state.reindex(bindings, hint)
        }

    }

    class AltPatternState(
        context: QueryContext,
        s: Subject,
        p: Alts,
        o: Object
    ) : TriplePatternState<Alts>(context, s, p, o) {

        private val states = p.allowed.map { p -> from(context, s, p, o) }

        override val cardinality: Cardinality
            get() = Cardinality(states.sumOf { it.cardinality.toDouble() })

        override val changeCount: Long
            get() = states.sumOf { it.changeCount }

        override fun process(delta: DataDelta) {
            states.forEach { it.process(delta) }
        }

        override fun peek(delta: DataAddition): Stream<Mapping> {
            // whilst the max cardinality here is not correct in all cases, it covers most bases
            return states.toStream().transform(maxCardinality = 1) { it.peek(delta) }
        }

        override fun peek(delta: DataDeletion): Stream<Mapping> {
            // whilst the max cardinality here is not correct in all cases, it covers most bases
            return states.toStream().transform(maxCardinality = 1) { it.peek(delta) }
        }

        override fun join(delta: MappingDelta): Stream<MappingDelta> {
            // stream creation here is cheap, already a list
            return states.toStream().transform(maxCardinality = states.maxOf { it.cardinality }) { it.join(delta) }
        }

        override fun reindex(bindings: BindingIdentifierSet, hint: MappingArrayHint) {
            states.forEach { it.reindex(bindings, hint) }
        }

    }

    class SimpleAltPatternState(
        context: QueryContext,
        s: Subject,
        p: SimpleAlts,
        o: Object
    ) : TriplePatternState<SimpleAlts>(context, s, p, o) {

        private val states = p.allowed.map { p -> from(context, s, p, o) }

        override val cardinality: Cardinality
            get() = Cardinality(states.sumOf { it.cardinality.toDouble() })

        override val changeCount: Long
            get() = states.sumOf { it.changeCount }

        override fun process(delta: DataDelta) {
            states.forEach { it.process(delta) }
        }

        override fun peek(delta: DataAddition): Stream<Mapping> {
            return states.toStream().transform(maxCardinality = 1) { it.peek(delta) }
        }

        override fun peek(delta: DataDeletion): Stream<Mapping> {
            return states.toStream().transform(maxCardinality = 1) { it.peek(delta) }
        }

        override fun join(delta: MappingDelta): Stream<MappingDelta> {
            // stream creation here is cheap, already a list
            return states.toStream().transform(maxCardinality = states.maxOf { it.cardinality }) { it.join(delta) }
        }

        override fun reindex(bindings: BindingIdentifierSet, hint: MappingArrayHint) {
            states.forEach { it.reindex(bindings, hint) }
        }

    }

    class SequencePatternState(
        context: QueryContext,
        s: Subject,
        p: Sequence,
        o: Object
    ) : TriplePatternState<Sequence>(context, s, p, o) {

        // we don't apply any filters here - we use anonymous bindings during the unfolding, so none could possibly
        //  match in the inner state
        private val tree = JoinTree.from(p.unfold(context, start = s, end = o), filters = emptyList())

        override val cardinality: Cardinality
            get() = tree.cardinality

        override var changeCount = 0L
            private set

        override fun process(delta: DataDelta) {
            val prev = tree.cardinality.value.toLong()
            tree.process(delta)
            // the change count for a sequence is a bit unique: as the main goal of the change count is to track
            //  how frequent it emits new mappings as a source, tracking the changes to individual segment elements
            //  is not very insightful (as it is possible these individual elements never make up a full sequence and
            //  thus do not contribute much to query performance)
            // therefore, the changecount for this element is represented by the changes observed in the cardinality of
            //  the sequence 'root' element
            // this means we have to convert the double representation, which may come with
            //  precision errors
            changeCount += (tree.cardinality.value.toLong() - prev).absoluteValue
        }

        override fun peek(delta: DataAddition): Stream<Mapping> {
            // the tree is built up using regular patterns only, meaning that there's a guarantee that all resulting
            //  solutions are additions
            return tree.peek(delta).mapped { it.value }
        }

        override fun join(delta: MappingDelta): Stream<MappingDelta> {
            return tree.join(delta)
        }

        override fun reindex(bindings: BindingIdentifierSet, hint: MappingArrayHint) {
            tree.reindex(bindings, hint)
        }

    }

    class UnboundedSequencePatternState(
        context: QueryContext,
        subj: Subject,
        pred: UnboundSequence,
        obj: Object
    ) : TriplePatternState<UnboundSequence>(context, subj, pred, obj) {

        // we don't apply any filters here - we use anonymous bindings during the unfolding, so none could possibly
        //  match in the inner state
        private val tree = JoinTree.from(pred.unfold(context, start = subj, end = obj), filters = emptyList())

        override val cardinality: Cardinality
            get() = tree.cardinality

        override var changeCount = 0L
            private set


        override fun process(delta: DataDelta) {
            val prev = tree.cardinality.value.toLong()
            tree.process(delta)
            // the change count for a sequence is a bit unique: as the main goal of the change count is to track
            //  how frequent it emits new mappings as a source, tracking the changes to individual segment elements
            //  is not very insightful (as it is possible these individual elements never make up a full sequence and
            //  thus do not contribute much to query performance)
            // therefore, the changecount for this element is represented by the changes observed in the cardinality of
            //  the sequence 'root' element
            // this means we have to convert the double representation, which may come with
            //  precision errors
            changeCount += (tree.cardinality.value.toLong() - prev).absoluteValue
        }

        override fun peek(delta: DataAddition): Stream<Mapping> {
            // the tree is built up using regular patterns only, meaning that there's a guarantee that all resulting
            //  solutions are additions
            return tree.peek(delta).mapped { it.value }
        }

        override fun join(delta: MappingDelta): Stream<MappingDelta> {
            return tree.join(delta)
        }

        override fun reindex(bindings: BindingIdentifierSet, hint: MappingArrayHint) {
            tree.reindex(bindings, hint)
        }

    }

    /* special triple pattern wrappers */

    /**
     * A special, optimized variant to filter specific types of triple patterns:
     *  knowing that the array backed variants directly store the result of what was [TriplePatternState.peek]ed
     *  upon [TriplePatternState.process]ing a data change, we only have to alter that peeked result stream by applying
     *  the [expr] filter once; at join time, the altered backing structure is used, so no additional filtering is
     *  required.
     */
    class FilteredArrayBackedTriplePatternState<P : Predicate>(
        context: QueryContext,
        private val inner: ArrayBackedPatternState<P>,
        private val expr: FilterExpression,
    ): ArrayBackedPatternState<P>(context, inner.s, inner.p, inner.o) {

        override val cardinality: Cardinality
            get() = inner.cardinality

        override var changeCount: Long
            get() = inner.changeCount
            set(value) { inner.changeCount = value }

        // we don't store the data ourselves; instead, we piggyback of our wrapped type's data instance
        override val data: ReindexableMappingArray = inner.data

        override fun peek(quad: EncodedQuad): Stream<Mapping> {
            // array-backed implementations use this adapted result stream to alter the data state, so we don't
            //  need to adapt the backing array any further; no additional filtering is required at `join()` time
            //  either (see description above)
            return inner.peek(quad).filtered { mapping -> expr.test(mapping) }
        }

        override fun stats(context: QueryContext, granularity: QueryStatistics.Granularity): Statistics {
            val inner = inner.stats(context, granularity)
            return if (granularity isAtLeast QueryStatistics.Granularity.HIGH_LEVEL) {
                Statistics.DescriptionElement(
                    description = "Filtered\n${expr}",
                    inner = inner,
                )
            } else {
                inner
            }
        }

    }

    /**
     * General variant of the [TriplePatternState] post [expr] filter. Should only be used to
     *  filter [TriplePatternState]s that cannot be filtered using the [FilteredArrayBackedTriplePatternState]
     */
    class FilteredTriplePatternState<P : Predicate>(
        context: QueryContext,
        private val inner: TriplePatternState<P>,
        private val expr: FilterExpression,
    ): TriplePatternState<P>(context, inner.s, inner.p, inner.o) {

        override val changeCount: Long
            get() = inner.changeCount

        init {
            // making sure we're not wrapping a type of triple pattern that can be filtered out more effectively
            check(inner !is ArrayBackedPatternState && inner !is FilteredArrayBackedTriplePatternState)
        }

        override fun peek(delta: DataAddition): Stream<Mapping> {
            return inner.peek(delta).filtered { mapping -> expr.test(mapping) }
        }

        override val cardinality: Cardinality
            get() = inner.cardinality

        override fun join(delta: MappingDelta): Stream<MappingDelta> {
            // we have to re-apply our filter as there is no direct (linear) relation between `peek` and `join` results
            //  in the general case
            return inner.join(delta).filtered { mapping -> expr.test(mapping.value) }
        }

        override fun reindex(
            bindings: BindingIdentifierSet,
            hint: MappingArrayHint
        ) {
            inner.reindex(bindings, hint)
        }

        override fun process(delta: DataDelta) {
            inner.process(delta)
        }

        override fun stats(context: QueryContext, granularity: QueryStatistics.Granularity): Statistics {
            val inner = inner.stats(context, granularity)
            return if (granularity isAtLeast QueryStatistics.Granularity.HIGH_LEVEL) {
                Statistics.DescriptionElement(
                    description = "Filtered\n${expr}",
                    inner = inner,
                )
            } else {
                inner
            }
        }

    }

    /* triple pattern API */

    /**
     * The sum of all insertions and deletions, used to track the 'stress' put on this specific triple pattern
     */
    abstract val changeCount: Long

    final override val bindings = bindingIdentifierSetOf(s, p, o)

    /**
     * Yields a new mapping on (subject-based) match:
     *  * when [s] is a [Binding], the [quad]'s [EncodedQuad.s] term ID is returned in a new [Mapping]
     *  * when [s] is an [Exact], an empty [Mapping] is returned instead, but only if the term ID
     *   matches (as this acts as a constraint)
     *
     * IMPORTANT: this method does not take the [p] (<-> [EncodedQuad.p]) or [o] (<-> [EncodedQuad.o]) values into
     *  account. The returned mapping, if any, still has to be altered to satisfy these two constraints.
     */
    protected fun subjectMappingOrNull(quad: EncodedQuad): Mapping? {
        return when (s) {
            is Binding -> mappingOf(context, s.id to TermIdentifier(quad.s))
            is Exact -> if (s.id.id == quad.s) context.emptyMapping() else null
        }
    }

    /**
     * Yields a new mapping on (object-based) match:
     *  * when [o] is a [Binding], the [quad]'s [EncodedQuad.o] term ID is returned in a new [Mapping]
     *  * when [o] is an [Exact], an empty [Mapping] is returned instead, but only if the term ID
     *   matches (as this acts as a constraint)
     *
     * IMPORTANT: this method does not take the [s] (<-> [EncodedQuad.s]) or [p] (<-> [EncodedQuad.p]) values into
     *  account. The returned mapping, if any, still has to be altered to satisfy these two constraints.
     */
    protected fun objectMappingOrNull(quad: EncodedQuad): Mapping? {
        return when (o) {
            is Binding -> mappingOf(context, o.id to TermIdentifier(quad.o))
            is Exact -> if (o.id.id == quad.o) context.emptyMapping() else null
        }
    }

    abstract fun peek(delta: DataAddition): Stream<Mapping>

    open fun peek(delta: DataDeletion): Stream<Mapping> = peek(
        delta = DataAddition(
            delta.value
        )
    )

    // triple patterns can only get new results upon getting new data and lose results upon removing data, so two
    //  specialised delta functions can be made instead, that are mapped here once
    final override fun peek(delta: DataDelta): OptimisedStream<MappingDelta> {
        return when (delta) {
            is DataAddition -> peek(delta).mapped { MappingAddition(it, origin = delta) }
            is DataDeletion -> peek(delta).mapped { MappingDeletion(it, origin = delta) }
        }.optimisedForReuse() // peek()s are already optimised, and mapping doesn't change that, so this is guaranteed to be a type wrapping
    }

    override fun stats(context: QueryContext, granularity: QueryStatistics.Granularity): Statistics {
        // avoiding 'generated' bindings, which lead with a ` `, to be formatted poorly
        fun Binding.bindingName(): String {
            val name = context.resolveBinding(id = this.id.id)
            return if (name[0] == ' ') "?_${name.substring(1)}" else "?$name"
        }

        val description = when (granularity) {
            QueryStatistics.Granularity.STRUCTURE_ONLY,
            QueryStatistics.Granularity.HIGH_LEVEL -> {
                return Statistics.SingleElement(cardinality = cardinality, changeCount = changeCount)
            }

            QueryStatistics.Granularity.DETAILED -> {

                val s = when (s) {
                    is Binding -> s.bindingName()
                    is Exact -> "<s>"
                }

                fun Predicate.description(): String = when (this) {
                    is Binding -> bindingName()
                    is Sequence -> chain.joinToString("/") { it.description() }
                    is UnboundSequence -> chain.joinToString("/") { it.description() }
                    is Exact -> "<p>"
                    is Negated -> "!${terms.description()}"
                    is SimpleAlts -> allowed.joinToString(", ", "(", ")") { it.description() }
                    is Alts -> allowed.joinToString(", ", "(", ")") { it.description() }
                    is OneOrMore -> "${element.description()}+"
                    is ZeroOrMore -> "${element.description()}*"
                }

                val p = p.description()
                val o = when (o) {
                    is Binding -> o.bindingName()
                    is Exact -> "<o>"
                }
                "$s $p $o"
            }

            QueryStatistics.Granularity.VERBOSE -> {
                fun Exact.decoded(): String {
                    return context.resolveTerm(id.id).toString()
                }

                fun Subject.decoded(): String = when (this) {
                    is Binding -> bindingName()
                    is Exact -> decoded()
                }
                fun Predicate.decoded(): String = when (this) {
                    is Binding -> bindingName()
                    is Sequence -> chain.joinToString("/") { it.decoded() }
                    is UnboundSequence -> chain.joinToString("/") { it.decoded() }
                    is Exact -> decoded()
                    is Negated -> "!${terms.decoded()}"
                    is SimpleAlts -> allowed.joinToString(", ", "(", ")") { it.decoded() }
                    is Alts -> allowed.joinToString(", ", "(", ")") { it.decoded() }
                    is OneOrMore -> "${element.decoded()}+"
                    is ZeroOrMore -> "${element.decoded()}*"
                }
                fun Object.decoded(): String = when (this) {
                    is Binding -> bindingName()
                    is Exact -> decoded()
                }

                "${s.decoded()} ${p.decoded()} ${o.decoded()}"
            }
        }
        val inner = Statistics.SingleElement(cardinality = cardinality, changeCount = changeCount)
        return Statistics.DescriptionElement(inner = inner, description = description)
    }

    final override fun toString() = "$s $p $o - cardinality $cardinality"

    final override fun filtered(filter: FilterExpression): TriplePatternState<*> {
        // we choose the most optimal filter wrapper based on the type we're wrapping
        return when (this) {
            is ArrayBackedPatternState<*> -> {
                FilteredArrayBackedTriplePatternState(
                    context = context,
                    inner = this,
                    expr = filter,
                )
            }
            else -> {
                FilteredTriplePatternState(
                    context = context,
                    inner = this,
                    expr = filter,
                )
            }
        }
    }

    @Suppress("FunctionName")
    companion object {

        private fun Subject(context: QueryContext, subject: TriplePattern.Subject): Subject {
            return when (subject) {
                is TriplePattern.Binding -> Binding(id = BindingIdentifier(context, subject.name))
                is TriplePattern.Exact -> Exact(id = TermIdentifier(context.resolveTerm(subject.term)))
            }
        }

        private fun Predicate(context: QueryContext, predicate: TriplePattern.Predicate): Predicate {
            return when (predicate) {
                is TriplePattern.GeneratedBinding -> Predicate(context, predicate)
                is TriplePattern.NamedBinding -> Predicate(context, predicate)
                is TriplePattern.Sequence -> Predicate(context, predicate)
                is TriplePattern.Exact -> Predicate(context, predicate)
                is TriplePattern.Negated -> Predicate(context, predicate)
                is TriplePattern.SimpleAlts -> Predicate(context, predicate)
                is TriplePattern.Alts -> Predicate(context, predicate)
                is TriplePattern.OneOrMore -> Predicate(context, predicate)
                is TriplePattern.ZeroOrMore -> Predicate(context, predicate)
                is TriplePattern.UnboundSequence -> Predicate(context, predicate)
            }
        }

        private fun Predicate(context: QueryContext, predicate: TriplePattern.UnboundPredicate): UnboundPredicate {
            return when (predicate) {
                is TriplePattern.Exact -> Predicate(context, predicate)
                is TriplePattern.Negated -> Predicate(context, predicate)
                is TriplePattern.SimpleAlts -> Predicate(context, predicate)
                is TriplePattern.Alts -> Predicate(context, predicate)
                is TriplePattern.OneOrMore -> Predicate(context, predicate)
                is TriplePattern.ZeroOrMore -> Predicate(context, predicate)
                is TriplePattern.UnboundSequence -> Predicate(context, predicate)
            }
        }

        private fun Predicate(context: QueryContext, predicate: TriplePattern.StatelessPredicate): StatelessPredicate {
            return when (predicate) {
                is TriplePattern.Exact -> Predicate(context, predicate)
                is TriplePattern.Negated -> Predicate(context, predicate)
                is TriplePattern.SimpleAlts -> Predicate(context, predicate)
            }
        }

        private fun Predicate(context: QueryContext, predicate: TriplePattern.Exact): Exact {
            return Exact(id = TermIdentifier(context.resolveTerm(predicate.term)))
        }

        private fun Predicate(context: QueryContext, predicate: TriplePattern.Binding): Binding {
            return Binding(id = BindingIdentifier(context, predicate.name))
        }

        private fun Predicate(context: QueryContext, predicate: TriplePattern.Negated): Negated {
            return Negated(terms = Predicate(context, predicate.terms))
        }

        private fun Predicate(context: QueryContext, predicate: TriplePattern.Alts): Alts {
            return Alts(allowed = predicate.allowed.map { Predicate(context, it) })
        }

        private fun Predicate(context: QueryContext, predicate: TriplePattern.SimpleAlts): SimpleAlts {
            return SimpleAlts(allowed = predicate.allowed.map { Predicate(context, it) })
        }

        private fun Predicate(context: QueryContext, predicate: TriplePattern.Sequence): Sequence {
            return Sequence(chain = predicate.chain.map { Predicate(context, it) })
        }

        private fun Predicate(context: QueryContext, predicate: TriplePattern.RepeatingPredicate): RepeatingPredicate {
            return when (predicate) {
                is TriplePattern.OneOrMore -> OneOrMore(element = Predicate(context, predicate.element))
                is TriplePattern.ZeroOrMore -> ZeroOrMore(element = Predicate(context, predicate.element))
            }
        }

        private fun Predicate(context: QueryContext, predicate: TriplePattern.UnboundSequence): UnboundSequence {
            return UnboundSequence(chain = predicate.chain.map { Predicate(context, it) })
        }

        private fun Object(context: QueryContext, obj: TriplePattern.Object): Object {
            return when (obj) {
                is TriplePattern.Binding -> Binding(id = BindingIdentifier(context, obj.name))
                is TriplePattern.Exact -> Exact(id = TermIdentifier(context.resolveTerm(obj.term)))
            }
        }

        fun from(context: QueryContext, pattern: TriplePattern): TriplePatternState<*> =
            from(context, pattern.s, pattern.p, pattern.o)

        fun from(
            context: QueryContext,
            s: TriplePattern.Subject,
            p: TriplePattern.Predicate,
            o: TriplePattern.Object
        ): TriplePatternState<*> = from(
            context = context,
            s = Subject(context, s),
            p = Predicate(context, p),
            o = Object(context, o),
        )

        fun from(
            context: QueryContext,
            s: Subject,
            p: Predicate,
            o: Object
        ): TriplePatternState<*> = when (p) {
            is Exact -> ExactPatternState(
                context = context,
                subj = s,
                pred = p,
                obj = o
            )

            is Negated -> NegatedPatternState(
                context = context,
                subj = s,
                pred = p,
                obj = o,
            )

            is Alts -> AltPatternState(
                context = context,
                s = s,
                p = p,
                o = o,
            )

            is SimpleAlts -> SimpleAltPatternState(
                context = context,
                s = s,
                p = p,
                o = o,
            )

            is Sequence -> SequencePatternState(
                context = context,
                s = s,
                p = p,
                o = o,
            )

            is RepeatingPredicate -> RepeatingPatternState(
                context = context,
                subj = s,
                pred = p,
                obj = o,
            )

            is UnboundSequence -> UnboundedSequencePatternState(
                context = context,
                subj = s,
                pred = p,
                obj = o,
            )

            is Binding -> BindingPatternState(
                context = context,
                subj = s,
                pred = p,
                obj = o,
            )
        }

    }

}
