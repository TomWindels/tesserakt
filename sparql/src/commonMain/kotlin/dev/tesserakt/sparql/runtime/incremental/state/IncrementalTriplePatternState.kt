package dev.tesserakt.sparql.runtime.incremental.state

import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.sparql.runtime.common.types.Pattern
import dev.tesserakt.sparql.runtime.core.Mapping
import dev.tesserakt.sparql.runtime.core.mappingOf
import dev.tesserakt.sparql.runtime.core.pattern.TriplePattern.Companion.bindingName
import dev.tesserakt.sparql.runtime.core.pattern.TriplePattern.Companion.matches
import dev.tesserakt.sparql.runtime.incremental.types.SegmentsList
import dev.tesserakt.util.compatibleWith

internal sealed class IncrementalTriplePatternState<P : Pattern.Predicate> {

    abstract val subj: Pattern.Subject
    abstract val pred: P
    abstract val obj: Pattern.Object

    // FIXME set semantics: duplicate triples should be ignored!

    data class ExactPattern(
        override val subj: Pattern.Subject,
        override val pred: Pattern.Exact,
        override val obj: Pattern.Object
    ) : IncrementalTriplePatternState<Pattern.Exact>() {

        private val data: MutableList<Mapping> = mutableListOf()

        override fun insert(quad: Quad) {
            // knowing how delta works here, this makes getting the mapping easier
            data.addAll(delta(quad))
        }

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

        override fun join(mappings: List<Mapping>): List<Mapping> = mappings.flatMap { bindings ->
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

    class Repeating(
        override val subj: Pattern.Subject,
        override val pred: Pattern.RepeatingPredicate,
        override val obj: Pattern.Object
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

}
