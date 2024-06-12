package dev.tesserakt.sparql.runtime.incremental.state

import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.sparql.runtime.core.Mapping
import dev.tesserakt.sparql.runtime.incremental.pattern.IncrementalBasicGraphPattern
import dev.tesserakt.sparql.runtime.incremental.types.Segment
import dev.tesserakt.sparql.runtime.incremental.types.SelectQuerySegment
import dev.tesserakt.sparql.runtime.incremental.types.StatementsSegment
import dev.tesserakt.sparql.runtime.incremental.types.Union

internal class IncrementalUnionState(union: Union) {

    sealed class Segment {

        class StatementsState(parent: StatementsSegment): Segment() {

            private val state = IncrementalBasicGraphPatternState(IncrementalBasicGraphPattern(parent.statements))

            override fun delta(quad: Quad): List<Mapping> {
                return state.delta(quad)
            }

            override fun insert(quad: Quad) {
                state.insert(quad)
            }

            override fun join(mappings: List<Mapping>): List<Mapping> {
                return state.join(mappings)
            }

        }

        class SubqueryState(parent: SelectQuerySegment): Segment() {

            override fun delta(quad: Quad): List<Mapping> {
                TODO("Not yet implemented")
            }

            override fun insert(quad: Quad) {
                TODO("Not yet implemented")
            }

            override fun join(mappings: List<Mapping>): List<Mapping> {
                TODO("Not yet implemented")
            }

        }

        abstract fun delta(quad: Quad): List<Mapping>

        abstract fun insert(quad: Quad)

        abstract fun join(mappings: List<Mapping>): List<Mapping>

    }

    private val state = union.map { it.createIncrementalSegmentState() }

    fun delta(quad: Quad): List<Mapping> {
        return state.flatMap { it.delta(quad) }
    }

    fun insert(quad: Quad) {
        return state.forEach { it.insert(quad) }
    }

    fun join(mappings: List<Mapping>): List<Mapping> {
        return state.fold(mappings) { results, s -> s.join(results) }
    }

}

/* helpers */

private fun Segment.createIncrementalSegmentState(): IncrementalUnionState.Segment = when (this) {
    is SelectQuerySegment -> IncrementalUnionState.Segment.SubqueryState(this)
    is StatementsSegment -> IncrementalUnionState.Segment.StatementsState(this)
}
