package dev.tesserakt.sparql.runtime.incremental.state

import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.sparql.runtime.core.Mapping
import dev.tesserakt.sparql.runtime.incremental.types.Segment
import dev.tesserakt.sparql.runtime.incremental.types.SelectQuerySegment
import dev.tesserakt.sparql.runtime.incremental.types.StatementsSegment
import dev.tesserakt.sparql.runtime.incremental.types.Union

internal class IncrementalUnionState(union: Union): JoinStateType {

    private sealed class Segment {

        class StatementsState(parent: StatementsSegment): Segment() {

            private val state = IncrementalBasicGraphPatternState(parent.statements)

            override val bindings: Set<String> get() = state.bindings

            override fun insert(quad: Quad): List<Mapping> {
                return state.insert(quad)
            }

            override fun join(mappings: List<Mapping>): List<Mapping> {
                return state.join(mappings)
            }

        }

        class SubqueryState(parent: SelectQuerySegment): Segment() {

            override val bindings: Set<String> = parent.query.output.map { it.name }.toSet()

            override fun insert(quad: Quad): List<Mapping> {
                TODO("Not yet implemented")
            }

            override fun join(mappings: List<Mapping>): List<Mapping> {
                TODO("Not yet implemented")
            }

        }

        abstract val bindings: Set<String>

        abstract fun insert(quad: Quad): List<Mapping>

        abstract fun join(mappings: List<Mapping>): List<Mapping>

    }

    private val state = union.map { it.createIncrementalSegmentState() }

    override val bindings: Set<String> = buildSet { state.forEach { addAll(it.bindings) } }

    override fun insert(quad: Quad): List<Mapping> {
        return state.flatMap { it.insert(quad) }
    }

    override fun join(mappings: List<Mapping>): List<Mapping> {
        return state.flatMap { s -> s.join(mappings) }
    }

    companion object {

        /* helpers */

        private fun dev.tesserakt.sparql.runtime.incremental.types.Segment.createIncrementalSegmentState() = when (this) {
            is SelectQuerySegment -> Segment.SubqueryState(this)
            is StatementsSegment -> Segment.StatementsState(this)
        }
    }

}
