package dev.tesserakt.sparql.runtime.incremental.state

import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.sparql.runtime.common.types.Pattern
import dev.tesserakt.sparql.runtime.common.util.Debug
import dev.tesserakt.sparql.runtime.common.util.getTermOrNull
import dev.tesserakt.sparql.runtime.core.Mapping
import dev.tesserakt.sparql.runtime.core.mappingOf
import dev.tesserakt.sparql.runtime.core.pattern.bindingName
import dev.tesserakt.sparql.runtime.core.pattern.matches
import dev.tesserakt.sparql.runtime.incremental.collection.HashJoinArray
import dev.tesserakt.sparql.runtime.incremental.types.SegmentsList

internal sealed class IncrementalTriplePatternState<P : Pattern.Predicate>(
    protected val s: Pattern.Subject, protected val p: P, protected val o: Pattern.Object
): JoinStateType {

    final override val bindings: Set<String> = bindingNamesOf(s, p, o)

    /**
     * Denotes the number of matches it contains, useful for quick cardinality calculations (e.g., joining this state
     *  on an empty solution results in [cardinality] results, or a size of 0 guarantees no results will get generated)
     */
    protected abstract val cardinality: Int

    sealed class ArrayBackedPattern<P : Pattern.Predicate>(
        subj: Pattern.Subject,
        pred: P,
        obj: Pattern.Object
    ) : IncrementalTriplePatternState<P>(subj, pred, obj) {

        private val data = HashJoinArray(bindingNamesOf(subj, pred, obj).toSet())

        override val cardinality get() = data.mappings.size

        final override fun process(quad: Quad) {
            val new = delta(quad)
            data.addAll(new)
        }

        final override fun join(mappings: List<Mapping>): List<Mapping> {
            Debug.onArrayPatternJoinExecuted()
            return data.join(mappings)
        }

    }

    data class ExactPattern(
        val subj: Pattern.Subject,
        val pred: Pattern.Exact,
        val obj: Pattern.Object
    ) : ArrayBackedPattern<Pattern.Exact>(subj, pred, obj) {

        override fun delta(quad: Quad): List<Mapping> {
            if (!subj.matches(quad.s) || !pred.matches(quad.p) || !obj.matches(quad.o)) {
                return emptyList()
            }
            // checking to see if there's any matches with the given triple
            val match = mappingOf(
                subj.bindingName to quad.s,
                obj.bindingName to quad.o
            )
            return listOf(match)
        }

    }

    data class BindingPattern(
        val subj: Pattern.Subject,
        val pred: Pattern.Binding,
        val obj: Pattern.Object
    ) : ArrayBackedPattern<Pattern.Binding>(subj, pred, obj) {

        override fun delta(quad: Quad): List<Mapping> {
            if (!subj.matches(quad.s) || !obj.matches(quad.o)) {
                return emptyList()
            }
            // checking to see if there's any matches with the given triple
            val match = mappingOf(
                subj.bindingName to quad.s,
                pred.name to quad.p,
                obj.bindingName to quad.o
            )
            return listOf(match)
        }

    }

    data class NegatedPattern(
        val subj: Pattern.Subject,
        val pred: Pattern.Negated,
        val obj: Pattern.Object
    ) : ArrayBackedPattern<Pattern.Negated>(subj, pred, obj) {

        override fun delta(quad: Quad): List<Mapping> {
            if (!subj.matches(quad.s) || pred.term == quad.p || !obj.matches(quad.o)) {
                return emptyList()
            }
            // checking to see if there's any matches with the given triple
            val match = mappingOf(
                subj.bindingName to quad.s,
                obj.bindingName to quad.o
            )
            return listOf(match)
        }

    }

    sealed class RepeatingPattern(
        subj: Pattern.Subject,
        pred: Pattern.RepeatingPredicate,
        obj: Pattern.Object
    ) : IncrementalTriplePatternState<Pattern.RepeatingPredicate>(subj, pred, obj) {

        protected val state = when (pred) {
            is Pattern.ZeroOrMore -> IncrementalPathState.zeroOrMore(
                start = subj,
                end = obj
            )

            is Pattern.OneOrMore -> IncrementalPathState.oneOrMore(
                start = subj,
                end = obj
            )
        }

        override val cardinality: Int get() = state.cardinality

        final override fun join(mappings: List<Mapping>): List<Mapping> {
            return state.join(mappings)
        }

    }

    class StatelessRepeatingPattern(
        subj: Pattern.Subject,
        pred: Pattern.RepeatingPredicate,
        obj: Pattern.Object
    ) : RepeatingPattern(subj, pred, obj) {

        private val predicate = pred.element

        override fun process(quad: Quad) {
            if (!predicate.matches(quad.p)) {
                return
            }
            val segment = SegmentsList.Segment(start = quad.s, end = quad.o)
            state.process(segment)
        }

        override fun delta(quad: Quad): List<Mapping> {
            if (!predicate.matches(quad.p)) {
                return emptyList()
            }
            val segment = SegmentsList.Segment(start = quad.s, end = quad.o)
            return state.delta(segment)
        }
    }

    class StatefulRepeatingPattern(
        s: Pattern.Subject,
        p: Pattern.RepeatingPredicate,
        o: Pattern.Object
    ) : RepeatingPattern(s, p, o) {

        private val predicate = Pattern(s, p.element, o).createIncrementalPatternState()

        override fun process(quad: Quad) {
            val new = predicate.delta(quad)
            val segments = new.map {
                SegmentsList.Segment(
                    start = this@StatefulRepeatingPattern.s.getTermOrNull(it)!!,
                    end = this@StatefulRepeatingPattern.o.getTermOrNull(it)!!
                )
            }
            predicate.process(quad)
            segments.forEach { segment -> state.process(segment) }
        }

        override fun delta(quad: Quad): List<Mapping> {
            val new = predicate.delta(quad)
            val segments = new.map {
                SegmentsList.Segment(
                    start = this@StatefulRepeatingPattern.s.getTermOrNull(it)!!,
                    end = this@StatefulRepeatingPattern.o.getTermOrNull(it)!!
                )
            }
            return segments
                .flatMapTo(mutableSetOf()) { segment -> state.delta(segment) }
                .toList()
        }

    }

    class AltPattern(
        s: Pattern.Subject,
        p: Pattern.Alts,
        o: Pattern.Object
    ) : IncrementalTriplePatternState<Pattern.Alts>(s, p, o) {

        private val states = p.allowed.map { p -> Pattern(s, p, o).createIncrementalPatternState() }

        override val cardinality: Int get() = states.sumOf { it.cardinality }

        override fun process(quad: Quad) {
            states.forEach { it.process(quad) }
        }

        override fun delta(quad: Quad): List<Mapping> {
            return states.flatMap { it.delta(quad) }
        }

        override fun join(mappings: List<Mapping>): List<Mapping> {
            return states.flatMap { it.join(mappings) }
        }

    }

    class UnboundedAltPattern(
        s: Pattern.Subject,
        p: Pattern.UnboundAlts,
        o: Pattern.Object
    ) : IncrementalTriplePatternState<Pattern.UnboundAlts>(s, p, o) {

        private val states = p.allowed.map { p -> Pattern(s, p, o).createIncrementalPatternState() }

        override val cardinality: Int get() = states.sumOf { it.cardinality }

        override fun process(quad: Quad) {
            states.forEach { it.process(quad) }
        }

        override fun delta(quad: Quad): List<Mapping> {
            return states.flatMap { it.delta(quad) }
        }

        override fun join(mappings: List<Mapping>): List<Mapping> {
            return states.flatMap { it.join(mappings) }
        }

    }

    class SequencePattern(
        s: Pattern.Subject,
        p: Pattern.Sequence,
        o: Pattern.Object
    ) : IncrementalTriplePatternState<Pattern.Sequence>(s, p, o) {

        private val tree: JoinTree

        private val mappings = mutableListOf<Mapping>()
        override val cardinality: Int get() = mappings.size

        init {
            require(p.chain.size > 1)
            val chain = ArrayList<Pattern>(p.chain.size)
            var start = s
            (0 until p.chain.size - 1).forEach { i ->
                val p = p.chain[i]
                val end = generateBinding()
                chain.add(Pattern(start, p, end))
                start = end.toSubject()
            }
            chain.add(Pattern(start, p.chain.last(), o))
            // FIXME
            tree = JoinTree.None(chain)
        }

        override fun process(quad: Quad) {
            tree.process(quad)
        }

        override fun delta(quad: Quad): List<Mapping> {
            return tree.delta(quad)
        }

        override fun join(mappings: List<Mapping>): List<Mapping> {
            return tree.join(mappings)
        }

    }

    class UnboundedSequencePattern(
        subj: Pattern.Subject,
        pred: Pattern.UnboundSequence,
        obj: Pattern.Object
    ) : IncrementalTriplePatternState<Pattern.UnboundSequence>(subj, pred, obj) {

        private val tree: JoinTree

        private val mappings = mutableListOf<Mapping>()
        override val cardinality: Int get() = mappings.size

        init {
            require(pred.chain.size > 1)
            val chain = ArrayList<Pattern>(pred.chain.size)
            var start = subj
            (0 until pred.chain.size - 1).forEach { i ->
                val p = pred.chain[i]
                val end = generateBinding()
                chain.add(Pattern(start, p, end))
                start = end.toSubject()
            }
            chain.add(Pattern(start, pred.chain.last(), obj))
            // FIXME
            tree = JoinTree.None(chain)
        }

        override fun process(quad: Quad) {
            tree.process(quad)
        }

        override fun delta(quad: Quad): List<Mapping> {
            return tree.delta(quad)
        }

        override fun join(mappings: List<Mapping>): List<Mapping> {
            return tree.join(mappings)
        }

    }

    final override fun toString() = "$s $p $o - cardinality $cardinality"

    companion object {

        fun Pattern.createIncrementalPatternState(): IncrementalTriplePatternState<*> = when (p) {
            is Pattern.RepeatingPredicate -> {
                when (p.element) {
                    is Pattern.Exact,
                    is Pattern.Negated -> StatelessRepeatingPattern(s, p, o)

                    is Pattern.UnboundAlts,
                    is Pattern.UnboundSequence,
                    is Pattern.OneOrMore,
                    is Pattern.ZeroOrMore -> StatefulRepeatingPattern(s, p, o)
                }
            }

            is Pattern.Exact -> ExactPattern(s, p, o)
            is Pattern.Negated -> NegatedPattern(s, p, o)
            is Pattern.Alts -> AltPattern(s, p, o)
            is Pattern.UnboundAlts -> UnboundedAltPattern(s, p, o)
            is Pattern.Sequence -> SequencePattern(s, p, o)
            is Pattern.UnboundSequence -> UnboundedSequencePattern(s, p, o)
            is Pattern.Binding -> BindingPattern(s, p, o)
        }

    }

}

/* helpers */

private fun Pattern.Object.toSubject(): Pattern.Subject = when (this) {
    is Pattern.GeneratedBinding -> this
    is Pattern.RegularBinding -> this
    is Pattern.Exact -> this
}

private var generatedBindingIndex = 0

private fun generateBinding() = Pattern.GeneratedBinding(id = generatedBindingIndex++)
