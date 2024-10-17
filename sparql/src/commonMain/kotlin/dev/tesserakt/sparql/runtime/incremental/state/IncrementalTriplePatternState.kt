package dev.tesserakt.sparql.runtime.incremental.state

import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.sparql.runtime.common.types.Pattern
import dev.tesserakt.sparql.runtime.common.util.getTermOrNull
import dev.tesserakt.sparql.runtime.core.Mapping
import dev.tesserakt.sparql.runtime.core.mappingOf
import dev.tesserakt.sparql.runtime.core.pattern.bindingName
import dev.tesserakt.sparql.runtime.core.pattern.matches
import dev.tesserakt.sparql.runtime.incremental.types.SegmentsList
import dev.tesserakt.sparql.runtime.util.Bitmask

internal sealed class IncrementalTriplePatternState<P : Pattern.Predicate> {

    // FIXME set semantics: duplicate triples should be ignored!
    sealed class ArrayBackedPattern<P : Pattern.Predicate> : IncrementalTriplePatternState<P>() {

        private val data = mutableListOf<Mapping>()

        final override fun insert(quad: Quad) {
            data.addAll(delta(quad))
        }

        final override fun join(mappings: List<Mapping>): List<Mapping> = mergeCompatibleMappings(data, mappings)

    }

    sealed class RepeatingPattern(
        subj: Pattern.Subject,
        pred: Pattern.RepeatingPredicate,
        obj: Pattern.Object
    ) : IncrementalTriplePatternState<Pattern.RepeatingPredicate>() {

        private val state = when (pred) {
            is Pattern.ZeroOrMore -> IncrementalPathState.ZeroOrMore(
                start = subj,
                end = obj
            )

            is Pattern.OneOrMore -> IncrementalPathState.OneOrMore(
                start = subj,
                end = obj
            )
        }

        final override fun delta(quad: Quad): List<Mapping> {
            return quad.toSegments()
                .flatMap { state.delta(it) }
                // it is possible for multiple segments to be returned, yielding duplicate available paths in this delta;
                //  distinct removes these superfluous duplicates
                .distinct()
        }

        final override fun join(mappings: List<Mapping>): List<Mapping> {
            return state.join(mappings)
        }

        override fun insert(quad: Quad) {
            quad.toSegments().forEach { state.insert(it) }
        }

        /**
         * Processes the incoming quad, returns an empty list if no matches are found, or its various segment objects
         */
        protected abstract fun Quad.toSegments(): Set<SegmentsList.Segment>

    }

    data class ExactPattern(
        val subj: Pattern.Subject,
        val pred: Pattern.Exact,
        val obj: Pattern.Object
    ) : ArrayBackedPattern<Pattern.Exact>() {

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
    ) : ArrayBackedPattern<Pattern.Exact>() {

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
    ) : ArrayBackedPattern<Pattern.Negated>() {

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

    class StatelessRepeatingPattern(
        private val subj: Pattern.Subject,
        pred: Pattern.RepeatingPredicate,
        private val obj: Pattern.Object
    ) : RepeatingPattern(subj, pred, obj) {

        private val predicate = pred.element

        override fun Quad.toSegments(): Set<SegmentsList.Segment> {
            if (!subj.matches(s) || !predicate.matches(p) || !obj.matches(o)) {
                return emptySet()
            }
            return setOf(SegmentsList.Segment(start = s, end = o))
        }

    }

    class StatefulRepeatingPattern(
        private val subj: Pattern.Subject,
        pred: Pattern.RepeatingPredicate,
        private val obj: Pattern.Object
    ) : RepeatingPattern(subj, pred, obj) {

        private val predicate = pred.element
        private val state = Pattern(subj, predicate, obj).createIncrementalPatternState()

        override fun Quad.toSegments(): Set<SegmentsList.Segment> {
            return state
                .delta(this)
                .map { SegmentsList.Segment(start = subj.getTermOrNull(it)!!, end = obj.getTermOrNull(it)!!) }
                .toSet()
        }

        override fun insert(quad: Quad) {
            state.insert(quad)
            super.insert(quad)
        }

    }

    class AltPattern(
        subj: Pattern.Subject,
        pred: Pattern.Alts,
        obj: Pattern.Object
    ) : IncrementalTriplePatternState<Pattern.Alts>() {

        private val states = pred.allowed.map { p -> Pattern(subj, p, obj).createIncrementalPatternState() }

        override fun delta(quad: Quad): List<Mapping> {
            return states.flatMap { it.delta(quad) }
        }

        override fun insert(quad: Quad) {
            states.forEach { it.insert(quad) }
        }

        override fun join(mappings: List<Mapping>): List<Mapping> {
            return states.flatMap { it.join(mappings) }
        }

    }

    class UnboundedAltPattern(
        subj: Pattern.Subject,
        pred: Pattern.UnboundAlts,
        obj: Pattern.Object
    ) : IncrementalTriplePatternState<Pattern.UnboundAlts>() {

        private val states = pred.allowed.map { p -> Pattern(subj, p, obj).createIncrementalPatternState() }

        override fun delta(quad: Quad): List<Mapping> {
            return states.flatMap { it.delta(quad) }
        }

        override fun insert(quad: Quad) {
            states.forEach { it.insert(quad) }
        }

        override fun join(mappings: List<Mapping>): List<Mapping> {
            return states.flatMap { it.join(mappings) }
        }

    }

    class SequencePattern(
        subj: Pattern.Subject,
        pred: Pattern.Sequence,
        obj: Pattern.Object
    ) : IncrementalTriplePatternState<Pattern.Sequence>() {

        private val chain: List<IncrementalTriplePatternState<*>>

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

        override fun delta(quad: Quad): List<Mapping> {
            // delta per chain element, joined with all other segments individually
            return chain
                .mapIndexed { i, element -> Bitmask.onesAt(i, length = chain.size) to element.delta(quad) }
                .expandResultSet()
                .flatMap { (mask, mappings) ->
                    mask.inv().fold(mappings) { results, i -> chain[i].join(results) }
                }
        }

        override fun insert(quad: Quad) {
            chain.forEach { it.insert(quad) }
        }

        override fun join(mappings: List<Mapping>): List<Mapping> {
            return chain.fold(mappings) { results, element -> element.join(results) }
        }

    }

    class UnboundedSequencePattern(
        subj: Pattern.Subject,
        pred: Pattern.UnboundSequence,
        obj: Pattern.Object
    ) : IncrementalTriplePatternState<Pattern.UnboundSequence>() {

        private val chain: List<IncrementalTriplePatternState<*>>

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

        override fun delta(quad: Quad): List<Mapping> {
            // delta per chain element, joined with all other segments individually
            return chain
                .mapIndexed { i, element -> Bitmask.onesAt(i, length = chain.size) to element.delta(quad) }
                .expandResultSet()
                .flatMap { (mask, mappings) -> mask.inv().fold(mappings) { results, i -> chain[i].join(results) } }
        }

        override fun insert(quad: Quad) {
            chain.forEach { it.insert(quad) }
        }

        override fun join(mappings: List<Mapping>): List<Mapping> {
            return chain.fold(mappings) { results, element -> element.join(results) }
        }

    }

    /**
     * Processes the incoming `input` `Quad`, calculating its impact as a `delta`, generating new resulting `Mapping`(s)
     *  if successful. IMPORTANT: this does **not** alter this state; see `insert(quad)`
     */
    abstract fun delta(quad: Quad): List<Mapping>

    /**
     * Updates the state to also account for the presence of the `input` `Quad`.
     */
    abstract fun insert(quad: Quad)

    /**
     * Joins the `input` list of `Mapping`s to generate new combined results using this state as a data reference
     */
    abstract fun join(mappings: List<Mapping>): List<Mapping>

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
