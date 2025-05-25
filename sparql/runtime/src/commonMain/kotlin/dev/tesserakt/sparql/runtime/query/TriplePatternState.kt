package dev.tesserakt.sparql.runtime.query

import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.sparql.runtime.RuntimeStatistics
import dev.tesserakt.sparql.runtime.collection.MappingArray
import dev.tesserakt.sparql.runtime.evaluation.*
import dev.tesserakt.sparql.runtime.evaluation.context.QueryContext
import dev.tesserakt.sparql.runtime.evaluation.mapping.Mapping
import dev.tesserakt.sparql.runtime.evaluation.mapping.mappingOf
import dev.tesserakt.sparql.runtime.stream.*
import dev.tesserakt.sparql.types.TriplePattern
import dev.tesserakt.sparql.types.matches
import dev.tesserakt.sparql.util.Cardinality

sealed class TriplePatternState<P : TriplePattern.Predicate>(
    val context: QueryContext,
    val s: TriplePattern.Subject,
    val p: P,
    val o: TriplePattern.Object
) : MutableJoinState {

    sealed class ArrayBackedPatternState<P : TriplePattern.Predicate>(
        context: QueryContext,
        subj: TriplePattern.Subject,
        pred: P,
        obj: TriplePattern.Object
    ) : TriplePatternState<P>(context, subj, pred, obj) {

        private val data = MappingArray(context, bindingNamesOf(subj, pred, obj))

        override val cardinality get() = data.cardinality

        final override fun process(delta: DataDelta) {
            when (delta) {
                is DataAddition -> {
                    val new = peek(delta)
                    data.addAll(new)
                }

                is DataDeletion -> {
                    val removed = peek(delta)
                    data.removeAll(removed)
                }
            }
        }

        final override fun join(delta: MappingDelta): Stream<MappingDelta> {
            RuntimeStatistics.onArrayPatternJoinExecuted()
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

        // as these are "stateless" compared to prior data, the operation type associated with the delta is irrelevant

        final override fun peek(delta: DataAddition): Stream<Mapping> {
            return peek(delta.value)
        }

        abstract fun peek(quad: Quad): Stream<Mapping>

    }

    class ExactPatternState(
        context: QueryContext,
        subj: TriplePattern.Subject,
        val pred: TriplePattern.Exact,
        obj: TriplePattern.Object
    ) : ArrayBackedPatternState<TriplePattern.Exact>(context, subj, pred, obj) {

        override fun peek(quad: Quad): Stream<Mapping> {
            if (pred.term != quad.p) {
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
        subj: TriplePattern.Subject,
        pred: TriplePattern.Binding,
        obj: TriplePattern.Object
    ) : ArrayBackedPatternState<TriplePattern.Binding>(context, subj, pred, obj) {

        override fun peek(quad: Quad): Stream<Mapping> {
            val s = subjectMappingOrNull(quad) ?: return emptyStream()
            val o = objectMappingOrNull(quad) ?: return emptyStream()
            val p = mappingOf(context, p.name to quad.p)
            val result = s.join(p)?.join(o) ?: return emptyStream()
            return streamOf(result)
        }

    }

    class NegatedPatternState(
        context: QueryContext,
        subj: TriplePattern.Subject,
        val pred: TriplePattern.Negated,
        obj: TriplePattern.Object
    ) : ArrayBackedPatternState<TriplePattern.Negated>(context, subj, pred, obj) {

        override fun peek(quad: Quad): Stream<Mapping> {
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
        subj: TriplePattern.Subject,
        pred: TriplePattern.RepeatingPredicate,
        obj: TriplePattern.Object
    ) : TriplePatternState<TriplePattern.RepeatingPredicate>(context, subj, pred, obj) {

        private val state = when (pred) {
            is TriplePattern.ZeroOrMore -> RepeatingPathState.zeroOrMore(
                context = context,
                start = subj,
                predicate = pred,
                end = obj
            )

            is TriplePattern.OneOrMore -> RepeatingPathState.oneOrMore(
                context = context,
                start = subj,
                predicate = pred,
                end = obj
            )
        }

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

    }

    class AltPatternState(
        context: QueryContext,
        s: TriplePattern.Subject,
        p: TriplePattern.Alts,
        o: TriplePattern.Object
    ) : TriplePatternState<TriplePattern.Alts>(context, s, p, o) {

        private val states = p.allowed.map { p -> from(context, s, p, o) }

        override val cardinality: Cardinality
            get() = Cardinality(states.sumOf { it.cardinality.toDouble() })

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

    }

    class SimpleAltPatternState(
        context: QueryContext,
        s: TriplePattern.Subject,
        p: TriplePattern.SimpleAlts,
        o: TriplePattern.Object
    ) : TriplePatternState<TriplePattern.SimpleAlts>(context, s, p, o) {

        private val states = p.allowed.map { p -> from(context, s, p, o) }

        override val cardinality: Cardinality
            get() = Cardinality(states.sumOf { it.cardinality.toDouble() })

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

    }

    class SequencePatternState(
        context: QueryContext,
        s: TriplePattern.Subject,
        p: TriplePattern.Sequence,
        o: TriplePattern.Object
    ) : TriplePatternState<TriplePattern.Sequence>(context, s, p, o) {

        private val tree = JoinTree(context, p.unfold(start = s, end = o))
        override val cardinality: Cardinality
            get() = tree.cardinality

        override fun process(delta: DataDelta) {
            tree.process(delta)
        }

        override fun peek(delta: DataAddition): Stream<Mapping> {
            // the tree is built up using regular patterns only, meaning that there's a guarantee that all resulting
            //  solutions are additions
            return tree.peek(delta).mapped { it.value }
        }

        override fun join(delta: MappingDelta): Stream<MappingDelta> {
            return tree.join(delta)
        }

    }

    class UnboundedSequencePatternState(
        context: QueryContext,
        subj: TriplePattern.Subject,
        pred: TriplePattern.UnboundSequence,
        obj: TriplePattern.Object
    ) : TriplePatternState<TriplePattern.UnboundSequence>(context, subj, pred, obj) {

        private val tree = JoinTree(context, pred.unfold(start = subj, end = obj))
        override val cardinality: Cardinality
            get() = tree.cardinality

        override fun process(delta: DataDelta) {
            tree.process(delta)
        }

        override fun peek(delta: DataAddition): Stream<Mapping> {
            // the tree is built up using regular patterns only, meaning that there's a guarantee that all resulting
            //  solutions are additions
            return tree.peek(delta).mapped { it.value }
        }

        override fun join(delta: MappingDelta): Stream<MappingDelta> {
            return tree.join(delta)
        }

    }

    final override val bindings: Set<String> = bindingNamesOf(s, p, o)

    /**
     * Yields a new mapping on (subject-based) match:
     *  * when [s] is a [TriplePattern.Binding], the [quad]'s [Quad.s] term is returned in a new [Mapping]
     *  * when [s] is a [TriplePattern.Exact], an empty [Mapping] is returned instead, but only if the term value
     *   matches (as this acts as a constraint)
     *
     * IMPORTANT: this method does not take the [p] (<-> [Quad.p]) or [o] (<-> [Quad.o]) values into account. The
     *  returned mapping, if any, still has to be altered to satisfy these two constraints.
     */
    protected fun subjectMappingOrNull(quad: Quad): Mapping? {
        return when (s) {
            is TriplePattern.Binding -> mappingOf(context, s.name to quad.s)
            is TriplePattern.Exact -> if (s.term == quad.s) context.emptyMapping() else null
        }
    }

    /**
     * Yields a new mapping on (subject-based) match:
     *  * when [o] is a [TriplePattern.Binding], the [quad]'s [Quad.o] term is returned in a new [Mapping]
     *  * when [o] is a [TriplePattern.Exact], an empty [Mapping] is returned instead, but only if the term value
     *   matches (as this acts as a constraint)
     *
     * IMPORTANT: this method does not take the [s] (<-> [Quad.s]) or [p] (<-> [Quad.p]) values into account. The
     *  returned mapping, if any, still has to be altered to satisfy these two constraints.
     */
    protected fun objectMappingOrNull(quad: Quad): Mapping? {
        return when (o) {
            is TriplePattern.Binding -> mappingOf(context, o.name to quad.o)
            is TriplePattern.Exact -> if (o.term == quad.o) context.emptyMapping() else null
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

    final override fun toString() = "$s $p $o - cardinality $cardinality"

    companion object {

        fun from(context: QueryContext, pattern: TriplePattern): TriplePatternState<*> =
            from(context, pattern.s, pattern.p, pattern.o)

        fun from(
            context: QueryContext,
            s: TriplePattern.Subject,
            p: TriplePattern.Predicate,
            o: TriplePattern.Object
        ): TriplePatternState<*> = when (p) {
            is TriplePattern.Exact -> ExactPatternState(context, s, p, o)
            is TriplePattern.Negated -> NegatedPatternState(context, s, p, o)
            is TriplePattern.Alts -> AltPatternState(context, s, p, o)
            is TriplePattern.SimpleAlts -> SimpleAltPatternState(context, s, p, o)
            is TriplePattern.Sequence -> SequencePatternState(context, s, p, o)
            is TriplePattern.RepeatingPredicate -> RepeatingPatternState(context, s, p, o)
            is TriplePattern.UnboundSequence -> UnboundedSequencePatternState(context, s, p, o)
            is TriplePattern.Binding -> BindingPatternState(context, s, p, o)
        }

    }

}
