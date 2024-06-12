package dev.tesserakt.sparql.runtime.incremental.state

import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.sparql.runtime.common.types.Pattern
import dev.tesserakt.sparql.runtime.core.Mapping
import dev.tesserakt.sparql.runtime.core.mappingOf
import dev.tesserakt.sparql.runtime.core.pattern.bindingName
import dev.tesserakt.sparql.runtime.core.pattern.matches
import dev.tesserakt.sparql.runtime.incremental.types.SegmentsList
import dev.tesserakt.util.compatibleWith

internal sealed class IncrementalTriplePatternState<P : Pattern.Predicate> {

    // FIXME set semantics: duplicate triples should be ignored!

    sealed class ArrayBackedPattern<P : Pattern.Predicate> : IncrementalTriplePatternState<P>() {

        private val data = mutableListOf<Mapping>()

        final override fun insert(quad: Quad) {
            data.addAll(delta(quad))
        }

        final override fun join(mappings: List<Mapping>): List<Mapping> = mappings.flatMap { bindings ->
            data.mapNotNull { previous ->
                // checking to see if there's any incompatibility in the input constraints
                if (bindings.compatibleWith(previous)) {
                    bindings + previous
                } else {
                    null
                }
            }
        }

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

    class NegatedPattern(
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

    class RepeatingPattern(
        subj: Pattern.Subject,
        val pred: Pattern.RepeatingPredicate,
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

        override fun delta(quad: Quad): List<Mapping> {
            return state.delta(quad.toSegment() ?: return emptyList())
        }

        override fun join(mappings: List<Mapping>): List<Mapping> {
            return state.join(mappings)
        }

        override fun insert(quad: Quad) {
            state.insert(quad.toSegment() ?: return)
        }

        /**
         * Processes the incoming triple, returns `null` if no match is found, or its segment object
         */
        private fun Quad.toSegment(): SegmentsList.Segment? {
            if (!pred.element.matches(p)) {
                return null
            }
            return SegmentsList.Segment(
                start = s,
                end = o
            )
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
            is Pattern.RepeatingPredicate -> RepeatingPattern(s, p, o)
            is Pattern.Exact -> ExactPattern(s, p, o)
            is Pattern.Alts -> AltPattern(s, p, o)
            is Pattern.Chain -> TODO()
            is Pattern.UnboundAlts -> TODO()
            is Pattern.UnboundChain -> TODO()
            is Pattern.Negated -> NegatedPattern(s, p, o)
            is Pattern.Binding -> TODO()
        }

    }

}
