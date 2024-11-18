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
import dev.tesserakt.sparql.runtime.incremental.collection.mutableJoinCollection
import dev.tesserakt.sparql.runtime.incremental.types.SegmentsList
import dev.tesserakt.sparql.runtime.util.Bitmask

internal sealed class IncrementalTriplePatternState<P : Pattern.Predicate>(
    protected val s: Pattern.Subject, protected val p: P, protected val o: Pattern.Object
): JoinStateType {

    protected abstract val mappings: List<Mapping>

    final override val bindings: Set<String> = bindingNamesOf(s, p, o)

    /**
     * Denotes the number of matches it contains, useful for quick cardinality calculations (e.g., joining this state
     *  on an empty solution results in [cardinality] results, or a size of 0 guarantees no results will get generated)
     */
    private val cardinality: Int get() = mappings.size

    sealed class ArrayBackedPattern<P : Pattern.Predicate>(
        subj: Pattern.Subject,
        pred: P,
        obj: Pattern.Object
    ) : IncrementalTriplePatternState<P>(subj, pred, obj) {

        private val data = HashJoinArray(bindingNamesOf(subj, pred, obj).toSet())

        override val mappings get() = data.mappings

        final override fun insert(quad: Quad): List<Mapping> {
            val new = delta(quad)
            data.addAll(new)
            return new
        }

        final override fun join(mappings: List<Mapping>): List<Mapping> {
            Debug.onArrayPatternJoinExecuted()
            return data.join(mappings)
        }

        protected abstract fun delta(quad: Quad): List<Mapping>

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
            is Pattern.ZeroOrMore -> IncrementalPathState.ZeroOrMore(
                start = subj,
                end = obj
            )

            is Pattern.OneOrMore -> IncrementalPathState.OneOrMore(
                start = subj,
                end = obj
            )
        }

        protected val arr = mutableJoinCollection(subj.bindingName, obj.bindingName)

        override val mappings get() = arr.mappings

        final override fun join(mappings: List<Mapping>): List<Mapping> {
            return arr.join(mappings)
        }

    }

    class StatelessRepeatingPattern(
        subj: Pattern.Subject,
        pred: Pattern.RepeatingPredicate,
        obj: Pattern.Object
    ) : RepeatingPattern(subj, pred, obj) {

        private val predicate = pred.element

        override fun insert(quad: Quad): List<Mapping> {
            if (!predicate.matches(quad.p)) {
                return emptyList()
            }
            val segment = SegmentsList.Segment(start = quad.s, end = quad.o)
            val new = state.insert(segment)
            arr.addAll(new)
            return new
        }
    }

    class StatefulRepeatingPattern(
        s: Pattern.Subject,
        p: Pattern.RepeatingPredicate,
        o: Pattern.Object
    ) : RepeatingPattern(s, p, o) {

        private val predicate = Pattern(s, p.element, o).createIncrementalPatternState()

        override fun insert(quad: Quad): List<Mapping> {
            val inner = predicate.insert(quad)
            val segments = inner.map {
                SegmentsList.Segment(
                    start = this@StatefulRepeatingPattern.s.getTermOrNull(it)!!,
                    end = this@StatefulRepeatingPattern.o.getTermOrNull(it)!!
                )
            }
            val new = segments.flatMap { segment -> state.insert(segment) }
            arr.addAll(new)
            return new
        }

    }

    class AltPattern(
        s: Pattern.Subject,
        p: Pattern.Alts,
        o: Pattern.Object
    ) : IncrementalTriplePatternState<Pattern.Alts>(s, p, o) {

        private val states = p.allowed.map { p -> Pattern(s, p, o).createIncrementalPatternState() }

        private val _mappings = mutableListOf<Mapping>()
        override val mappings: List<Mapping> = _mappings

        override fun insert(quad: Quad): List<Mapping> {
            val new = states.flatMap { it.insert(quad) }
            _mappings.addAll(new)
            return new
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

        private val _mappings = mutableListOf<Mapping>()
        override val mappings: List<Mapping> = _mappings

        override fun insert(quad: Quad): List<Mapping> {
            val new = states.flatMap { it.insert(quad) }
            _mappings.addAll(new)
            return new
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

        private val chain: List<IncrementalTriplePatternState<*>>

        private val _mappings = mutableListOf<Mapping>()
        override val mappings: List<Mapping> get() = _mappings

        init {
            require(p.chain.size > 1)
            chain = ArrayList(p.chain.size)
            var start = s
            (0 until p.chain.size - 1).forEach { i ->
                val p = p.chain[i]
                val end = generateBinding()
                chain.add(Pattern(start, p, end).createIncrementalPatternState())
                start = end.toSubject()
            }
            chain.add(Pattern(start, p.chain.last(), o).createIncrementalPatternState())
        }

        override fun insert(quad: Quad): List<Mapping> {
            // TODO: join tree
            val new = chain
                .mapIndexed { i, element -> Bitmask.onesAt(i, length = chain.size) to element.insert(quad) }
                .expandResultSet()
                .flatMap { (mask, mappings) ->
                    mask.inv().fold(mappings) { results, i -> chain[i].join(results) }
                }
            _mappings.addAll(new)
            return new
        }

        override fun join(mappings: List<Mapping>): List<Mapping> {
            return chain.fold(mappings) { results, element -> element.join(results) }
        }

    }

    class UnboundedSequencePattern(
        subj: Pattern.Subject,
        pred: Pattern.UnboundSequence,
        obj: Pattern.Object
    ) : IncrementalTriplePatternState<Pattern.UnboundSequence>(subj, pred, obj) {

        private val chain: List<IncrementalTriplePatternState<*>>

        private val _mappings = mutableListOf<Mapping>()
        override val mappings: List<Mapping> get() = _mappings

        init {
            require(pred.chain.size > 1)
            chain = ArrayList(pred.chain.size)
            var start = subj
            (0 until pred.chain.size - 1).forEach { i ->
                val p = pred.chain[i]
                val end = generateBinding()
                chain.add(Pattern(start, p, end).createIncrementalPatternState())
                start = end.toSubject()
            }
            chain.add(Pattern(start, pred.chain.last(), obj).createIncrementalPatternState())
        }

        override fun insert(quad: Quad): List<Mapping> {
            // TODO: join tree
            val new = chain
                .mapIndexed { i, element -> Bitmask.onesAt(i, length = chain.size) to element.insert(quad) }
                .expandResultSet()
                .flatMap { (mask, mappings) ->
                    mask.inv().fold(mappings) { results, i -> chain[i].join(results) }
                }
            _mappings.addAll(new)
            chain.forEach { it.insert(quad) }
            return new
        }

        override fun join(mappings: List<Mapping>): List<Mapping> {
            return chain.fold(mappings) { results, element -> element.join(results) }
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
