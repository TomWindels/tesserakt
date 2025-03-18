package dev.tesserakt.sparql.runtime.query

import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.sparql.runtime.RuntimeStatistics
import dev.tesserakt.sparql.runtime.collection.MappingArray
import dev.tesserakt.sparql.runtime.evaluation.*
import dev.tesserakt.sparql.runtime.stream.*
import dev.tesserakt.sparql.types.TriplePattern
import dev.tesserakt.sparql.types.bindingName
import dev.tesserakt.sparql.types.matches
import dev.tesserakt.sparql.util.Cardinality

sealed class TriplePatternState<P : TriplePattern.Predicate>(
    val s: TriplePattern.Subject,
    val p: P,
    val o: TriplePattern.Object
): MutableJoinState {

    sealed class ArrayBackedPatternState<P : TriplePattern.Predicate>(
        subj: TriplePattern.Subject,
        pred: P,
        obj: TriplePattern.Object
    ) : TriplePatternState<P>(subj, pred, obj) {

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

    data class ExactPatternState(
        val subj: TriplePattern.Subject,
        val pred: TriplePattern.Exact,
        val obj: TriplePattern.Object
    ) : ArrayBackedPatternState<TriplePattern.Exact>(subj, pred, obj) {

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

    data class BindingPatternState(
        val subj: TriplePattern.Subject,
        val pred: TriplePattern.Binding,
        val obj: TriplePattern.Object
    ) : ArrayBackedPatternState<TriplePattern.Binding>(subj, pred, obj) {

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

    data class NegatedPatternState(
        val subj: TriplePattern.Subject,
        val pred: TriplePattern.Negated,
        val obj: TriplePattern.Object
    ) : ArrayBackedPatternState<TriplePattern.Negated>(subj, pred, obj) {

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

    class RepeatingPatternState(
        subj: TriplePattern.Subject,
        pred: TriplePattern.RepeatingPredicate,
        obj: TriplePattern.Object
    ) : TriplePatternState<TriplePattern.RepeatingPredicate>(subj, pred, obj) {

        private val state = when (pred) {
            is TriplePattern.ZeroOrMore -> RepeatingPathState.zeroOrMore(
                start = subj,
                predicate = pred,
                end = obj
            )

            is TriplePattern.OneOrMore -> RepeatingPathState.oneOrMore(
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
        s: TriplePattern.Subject,
        p: TriplePattern.Alts,
        o: TriplePattern.Object
    ) : TriplePatternState<TriplePattern.Alts>(s, p, o) {

        private val states = p.allowed.map { p -> from(s, p, o) }

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
        s: TriplePattern.Subject,
        p: TriplePattern.SimpleAlts,
        o: TriplePattern.Object
    ) : TriplePatternState<TriplePattern.SimpleAlts>(s, p, o) {

        private val states = p.allowed.map { p -> from(s, p, o) }

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
        s: TriplePattern.Subject,
        p: TriplePattern.Sequence,
        o: TriplePattern.Object
    ) : TriplePatternState<TriplePattern.Sequence>(s, p, o) {

        private val tree = JoinTree(p.unfold(start = s, end = o))
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
        subj: TriplePattern.Subject,
        pred: TriplePattern.UnboundSequence,
        obj: TriplePattern.Object
    ) : TriplePatternState<TriplePattern.UnboundSequence>(subj, pred, obj) {

        private val tree = JoinTree(pred.unfold(start = subj, end = obj))
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

        fun from(pattern: TriplePattern): TriplePatternState<*> = from(pattern.s, pattern.p, pattern.o)

        fun from(
            s: TriplePattern.Subject,
            p: TriplePattern.Predicate,
            o: TriplePattern.Object
        ): TriplePatternState<*> = when (p) {
            is TriplePattern.Exact -> ExactPatternState(s, p, o)
            is TriplePattern.Negated -> NegatedPatternState(s, p, o)
            is TriplePattern.Alts -> AltPatternState(s, p, o)
            is TriplePattern.SimpleAlts -> SimpleAltPatternState(s, p, o)
            is TriplePattern.Sequence -> SequencePatternState(s, p, o)
            is TriplePattern.RepeatingPredicate -> RepeatingPatternState(s, p, o)
            is TriplePattern.UnboundSequence -> UnboundedSequencePatternState(s, p, o)
            is TriplePattern.Binding -> BindingPatternState(s, p, o)
        }

    }

}
