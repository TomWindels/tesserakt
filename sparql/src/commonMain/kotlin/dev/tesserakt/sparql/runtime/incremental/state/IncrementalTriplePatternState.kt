package dev.tesserakt.sparql.runtime.incremental.state

import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.sparql.runtime.core.Mapping
import dev.tesserakt.sparql.runtime.core.mappingOf
import dev.tesserakt.sparql.runtime.core.pattern.TriplePattern
import dev.tesserakt.sparql.runtime.core.pattern.TriplePattern.Companion.bindingName
import dev.tesserakt.sparql.runtime.core.pattern.TriplePattern.Companion.matches
import dev.tesserakt.sparql.runtime.incremental.types.SegmentsList
import dev.tesserakt.util.compatibleWith

internal sealed class IncrementalTriplePatternState {

    sealed class NonRepeating : IncrementalTriplePatternState() {

        abstract val pattern: TriplePattern.NonRepeating

        data class ArrayBacked(
            override val pattern: TriplePattern.NonRepeating,
            override val data: MutableList<Mapping> = mutableListOf()
        ) : NonRepeating() {

            override fun insert(quad: Quad) {
                // knowing how delta works here, this makes getting the mapping easier
                data.addAll(delta(quad))
            }

        }

        // TODO: later, a circular buffer-backed implementation can be added

        protected abstract val data: List<Mapping>

        final override fun delta(quad: Quad): List<Mapping> {
            if (!pattern.s.matches(quad.s) || !pattern.p.matches(quad.p) || !pattern.o.matches(quad.o)) {
                return emptyList()
            }
            // checking to see if there's any matches with the given triple
            val match = mappingOf(
                pattern.s.bindingName to quad.s,
                pattern.p.bindingName to quad.p,
                pattern.o.bindingName to quad.o
            )
            return listOf(match)
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

    class Repeating(private val pattern: TriplePattern.Repeating) : IncrementalTriplePatternState() {

        private val state = when (pattern.type) {
            TriplePattern.Repeating.Type.ZERO_OR_MORE -> IncrementalPathState.ZeroOrMore(start = pattern.s, end = pattern.o)
            TriplePattern.Repeating.Type.ONE_OR_MORE -> IncrementalPathState.OneOrMore(start = pattern.s, end = pattern.o)
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
            if (!pattern.p.matches(p)) {
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
