package dev.tesserakt.sparql.runtime.incremental.state

import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.sparql.runtime.common.types.Pattern
import dev.tesserakt.sparql.runtime.common.util.Debug
import dev.tesserakt.sparql.runtime.common.util.getTermOrNull
import dev.tesserakt.sparql.runtime.core.Mapping
import dev.tesserakt.sparql.runtime.core.mappingOf
import dev.tesserakt.sparql.runtime.core.pattern.bindingName
import dev.tesserakt.sparql.runtime.core.pattern.matches
import dev.tesserakt.sparql.runtime.incremental.collection.mutableJoinCollection
import dev.tesserakt.sparql.runtime.incremental.delta.Delta
import dev.tesserakt.sparql.runtime.incremental.types.SegmentsList

internal sealed class IncrementalTriplePatternState<P : Pattern.Predicate>(
    protected val s: Pattern.Subject, protected val p: P, protected val o: Pattern.Object
): MutableJoinState {

    sealed class ArrayBackedPattern<P : Pattern.Predicate>(
        subj: Pattern.Subject,
        pred: P,
        obj: Pattern.Object
    ) : IncrementalTriplePatternState<P>(subj, pred, obj) {

        private val data = mutableJoinCollection(bindingNamesOf(subj, pred, obj))

        override val cardinality get() = data.mappings.size

        final override fun process(delta: Delta.Data) {
            when (delta) {
                is Delta.DataAddition -> {
                    val new = peek(delta)
                    data.addAll(new)
                }
            }
        }

        final override fun join(mapping: Mapping): List<Mapping> {
            Debug.onArrayPatternJoinExecuted()
            return data.join(mapping)
        }

        // as these are "stateless" compared to prior data, the operation type associated with the delta is irrelevant

        final override fun peek(delta: Delta.DataAddition): List<Mapping> {
            return peek(delta.value)
        }

        abstract fun peek(quad: Quad): List<Mapping>

    }

    data class ExactPattern(
        val subj: Pattern.Subject,
        val pred: Pattern.Exact,
        val obj: Pattern.Object
    ) : ArrayBackedPattern<Pattern.Exact>(subj, pred, obj) {

        override fun peek(quad: Quad): List<Mapping> {
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

        override fun peek(quad: Quad): List<Mapping> {
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

        override fun peek(quad: Quad): List<Mapping> {
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

        final override fun join(mapping: Mapping): List<Mapping> {
            // TODO(perf) state API improvements re: single mapping insertion
            return state.join(listOf(mapping))
        }

    }

    class StatelessRepeatingPattern(
        subj: Pattern.Subject,
        pred: Pattern.RepeatingPredicate,
        obj: Pattern.Object
    ) : RepeatingPattern(subj, pred, obj) {

        private val predicate = pred.element

        override fun process(delta: Delta.Data) {
            val quad = delta.value
            if (!predicate.matches(quad.p)) {
                return
            }
            when (delta) {
                is Delta.DataAddition -> {
                    val segment = SegmentsList.Segment(start = quad.s, end = quad.o)
                    state.process(segment)
                }
            }
        }

        override fun peek(delta: Delta.DataAddition): List<Mapping> {
            val quad = delta.value
            if (!predicate.matches(quad.p)) {
                return emptyList()
            }
            val segment = SegmentsList.Segment(start = quad.s, end = quad.o)
            return state.peek(segment)
        }
    }

    class StatefulRepeatingPattern(
        s: Pattern.Subject,
        p: Pattern.RepeatingPredicate,
        o: Pattern.Object
    ) : RepeatingPattern(s, p, o) {

        private val predicate = Pattern(s, p.element, o).createIncrementalPatternState()

        override fun process(delta: Delta.Data) {
            when (delta) {
                is Delta.DataAddition -> {
                    val new = predicate.peek(delta)
                    val segments = new.map {
                        SegmentsList.Segment(
                            start = this@StatefulRepeatingPattern.s.getTermOrNull(it)!!,
                            end = this@StatefulRepeatingPattern.o.getTermOrNull(it)!!
                        )
                    }
                    predicate.process(delta)
                    segments.forEach { segment -> state.process(segment) }
                }
            }
        }

        override fun peek(delta: Delta.DataAddition): List<Mapping> {
            val new = predicate.peek(delta)
            val segments = new.map {
                SegmentsList.Segment(
                    start = this@StatefulRepeatingPattern.s.getTermOrNull(it)!!,
                    end = this@StatefulRepeatingPattern.o.getTermOrNull(it)!!
                )
            }
            return segments
                .flatMapTo(mutableSetOf()) { segment -> state.peek(segment) }
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

        override fun process(delta: Delta.Data) {
            states.forEach { it.process(delta) }
        }

        override fun peek(delta: Delta.DataAddition): List<Mapping> {
            return states.flatMap { it.peek(delta) }
        }

        override fun join(mapping: Mapping): List<Mapping> {
            return states.flatMap { it.join(mapping) }
        }

    }

    class UnboundedAltPattern(
        s: Pattern.Subject,
        p: Pattern.UnboundAlts,
        o: Pattern.Object
    ) : IncrementalTriplePatternState<Pattern.UnboundAlts>(s, p, o) {

        private val states = p.allowed.map { p -> Pattern(s, p, o).createIncrementalPatternState() }

        override val cardinality: Int get() = states.sumOf { it.cardinality }

        override fun process(delta: Delta.Data) {
            states.forEach { it.process(delta) }
        }

        override fun peek(delta: Delta.DataAddition): List<Mapping> {
            return states.flatMap { it.peek(delta) }
        }

        override fun join(mapping: Mapping): List<Mapping> {
            return states.flatMap { it.join(mapping) }
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
            tree = JoinTree(chain)
        }

        override fun process(delta: Delta.Data) {
            tree.process(delta)
        }

        override fun peek(delta: Delta.DataAddition): List<Mapping> {
            // the tree is built up using regular patterns only, meaning that there's a guarantee that all resulting
            //  solutions are additions
            return tree.peek(delta).map { it.value }
        }

        override fun join(mapping: Mapping): List<Mapping> {
            // the tree is built up using regular patterns only, meaning that evaluating everything as an addition
            //  should give correct results
            return tree.join(Delta.BindingsAddition(mapping)).map { it.value }
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
            tree = JoinTree(chain)
        }

        override fun process(delta: Delta.Data) {
            tree.process(delta)
        }

        override fun peek(delta: Delta.DataAddition): List<Mapping> {
            // the tree is built up using regular patterns only, meaning that there's a guarantee that all resulting
            //  solutions are additions
            return tree.peek(delta).map { it.value }
        }

        override fun join(mapping: Mapping): List<Mapping> {
            // the tree is built up using regular patterns only, meaning that evaluating everything as an addition
            //  should give correct results
            return tree.join(Delta.BindingsAddition(mapping)).map { it.value }
        }

    }

    final override val bindings: Set<String> = bindingNamesOf(s, p, o)

    /**
     * Denotes the number of matches it contains, useful for quick cardinality calculations (e.g., joining this state
     *  on an empty solution results in [cardinality] results, or a size of 0 guarantees no results will get generated)
     */
    protected abstract val cardinality: Int

    abstract fun peek(delta: Delta.DataAddition): List<Mapping>

    abstract fun join(mapping: Mapping): List<Mapping>

    // triple patterns can only get new results upon getting new data and lose results upon removing data, so two
    //  specialised delta functions can be made instead, that are mapped here once
    final override fun peek(delta: Delta.Data): List<Delta.Bindings> {
        return when (delta) {
            is Delta.DataAddition -> peek(delta).map { Delta.BindingsAddition(it) }
        }
    }

    // triple patterns can only get new results upon getting new data and lose results upon removing data, so two
    //  specialised delta functions can be made instead, that are mapped here once
    final override fun join(delta: Delta.Bindings): List<Delta.Bindings> {
        return when (delta) {
            is Delta.BindingsAddition -> join(delta.value).map { Delta.BindingsAddition(it) }
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
