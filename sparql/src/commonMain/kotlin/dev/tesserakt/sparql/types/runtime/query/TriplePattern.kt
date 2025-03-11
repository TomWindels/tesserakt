package dev.tesserakt.sparql.types.runtime.query

import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.sparql.debug.Debug
import dev.tesserakt.sparql.types.runtime.collection.MappingArray
import dev.tesserakt.sparql.types.runtime.element.Pattern
import dev.tesserakt.sparql.types.runtime.element.bindingName
import dev.tesserakt.sparql.types.runtime.element.matches
import dev.tesserakt.sparql.types.runtime.evaluation.*
import dev.tesserakt.sparql.types.runtime.stream.*
import dev.tesserakt.sparql.types.util.Cardinality

internal sealed class TriplePattern<P : Pattern.Predicate>(
    val s: Pattern.Subject,
    val p: P,
    val o: Pattern.Object
): MutableJoinState {

    sealed class ArrayBackedPattern<P : Pattern.Predicate>(
        subj: Pattern.Subject,
        pred: P,
        obj: Pattern.Object
    ) : TriplePattern<P>(subj, pred, obj) {

        private val data = MappingArray(bindingNamesOf(subj, pred, obj))

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
            Debug.onArrayPatternJoinExecuted()
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

    data class ExactPattern(
        val subj: Pattern.Subject,
        val pred: Pattern.Exact,
        val obj: Pattern.Object
    ) : ArrayBackedPattern<Pattern.Exact>(subj, pred, obj) {

        override fun peek(quad: Quad): Stream<Mapping> {
            if (!subj.matches(quad.s) || !pred.matches(quad.p) || !obj.matches(quad.o)) {
                return emptyStream()
            }
            // checking to see if there's any matches with the given triple
            val match = mappingOf(
                subj.bindingName to quad.s,
                obj.bindingName to quad.o
            )
            return streamOf(match)
        }

    }

    data class BindingPattern(
        val subj: Pattern.Subject,
        val pred: Pattern.Binding,
        val obj: Pattern.Object
    ) : ArrayBackedPattern<Pattern.Binding>(subj, pred, obj) {

        override fun peek(quad: Quad): Stream<Mapping> {
            if (!subj.matches(quad.s) || !obj.matches(quad.o)) {
                return emptyStream()
            }
            // checking to see if there's any matches with the given triple
            val match = mappingOf(
                subj.bindingName to quad.s,
                pred.name to quad.p,
                obj.bindingName to quad.o
            )
            return streamOf(match)
        }

    }

    data class NegatedPattern(
        val subj: Pattern.Subject,
        val pred: Pattern.Negated,
        val obj: Pattern.Object
    ) : ArrayBackedPattern<Pattern.Negated>(subj, pred, obj) {

        override fun peek(quad: Quad): Stream<Mapping> {
            if (!subj.matches(quad.s) || pred.term == quad.p || !obj.matches(quad.o)) {
                return emptyStream()
            }
            // checking to see if there's any matches with the given triple
            val match = mappingOf(
                subj.bindingName to quad.s,
                obj.bindingName to quad.o
            )
            return streamOf(match)
        }

    }

    class RepeatingPattern(
        subj: Pattern.Subject,
        pred: Pattern.RepeatingPredicate,
        obj: Pattern.Object
    ) : TriplePattern<Pattern.RepeatingPredicate>(subj, pred, obj) {

        private val state = when (pred) {
            is Pattern.ZeroOrMore -> RepeatingPath.zeroOrMore(
                start = subj,
                predicate = pred,
                end = obj
            )

            is Pattern.OneOrMore -> RepeatingPath.oneOrMore(
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

    class AltPattern(
        s: Pattern.Subject,
        p: Pattern.Alts,
        o: Pattern.Object
    ) : TriplePattern<Pattern.Alts>(s, p, o) {

        private val states = p.allowed.map { p -> Pattern(s, p, o).createIncrementalPatternState() }

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

    class SimpleAltPattern(
        s: Pattern.Subject,
        p: Pattern.SimpleAlts,
        o: Pattern.Object
    ) : TriplePattern<Pattern.SimpleAlts>(s, p, o) {

        private val states = p.allowed.map { p -> Pattern(s, p, o).createIncrementalPatternState() }

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

    class SequencePattern(
        s: Pattern.Subject,
        p: Pattern.Sequence,
        o: Pattern.Object
    ) : TriplePattern<Pattern.Sequence>(s, p, o) {

        private val tree = JoinTree(p.unfold(start = s, end = o))
        private val mappings = mutableListOf<Mapping>()
        override val cardinality: Cardinality
            get() = Cardinality(mappings.size)

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

    class UnboundedSequencePattern(
        subj: Pattern.Subject,
        pred: Pattern.UnboundSequence,
        obj: Pattern.Object
    ) : TriplePattern<Pattern.UnboundSequence>(subj, pred, obj) {

        private val tree = JoinTree(pred.unfold(start = subj, end = obj))
        private val mappings = mutableListOf<Mapping>()
        override val cardinality: Cardinality
            get() = Cardinality(mappings.size)

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

    abstract fun peek(delta: DataAddition): Stream<Mapping>

    open fun peek(delta: DataDeletion): Stream<Mapping> = peek(delta = DataAddition(
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

        fun Pattern.createIncrementalPatternState(): TriplePattern<*> = when (p) {
            is Pattern.Exact -> ExactPattern(s, p, o)
            is Pattern.Negated -> NegatedPattern(s, p, o)
            is Pattern.Alts -> AltPattern(s, p, o)
            is Pattern.SimpleAlts -> SimpleAltPattern(s, p, o)
            is Pattern.Sequence -> SequencePattern(s, p, o)
            is Pattern.RepeatingPredicate -> RepeatingPattern(s, p, o)
            is Pattern.UnboundSequence -> UnboundedSequencePattern(s, p, o)
            is Pattern.Binding -> BindingPattern(s, p, o)
        }

    }

}
