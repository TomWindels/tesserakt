package dev.tesserakt.sparql.runtime.incremental.state

import dev.tesserakt.sparql.runtime.incremental.delta.DataDelta
import dev.tesserakt.sparql.runtime.incremental.delta.MappingDelta
import dev.tesserakt.sparql.runtime.incremental.stream.*
import dev.tesserakt.sparql.runtime.incremental.types.Cardinality
import dev.tesserakt.sparql.runtime.incremental.types.SelectQuerySegment
import dev.tesserakt.sparql.runtime.incremental.types.StatementsSegment
import dev.tesserakt.sparql.runtime.incremental.types.Union

internal class IncrementalUnionState(union: Union): MutableJoinState {

    private sealed class Segment {

        class StatementsState(parent: StatementsSegment): Segment() {

            private val state = IncrementalBasicGraphPatternState(parent.statements)

            override val bindings: Set<String> get() = state.bindings

            override val cardinality: Cardinality
                get() = state.cardinality

            override fun peek(delta: DataDelta): Stream<MappingDelta> {
                return state.peek(delta)
            }

            override fun process(delta: DataDelta) {
                return state.process(delta)
            }

            override fun join(delta: MappingDelta): Stream<MappingDelta> {
                return state.join(delta)
            }

        }

        class SubqueryState(parent: SelectQuerySegment): Segment() {

            override val bindings: Set<String> = parent.query.output.map { it.name }.toSet()

            override val cardinality: Cardinality
                get() = TODO("Not yet implemented")

            override fun peek(delta: DataDelta): Stream<MappingDelta> {
                TODO("Not yet implemented")
            }

            override fun process(delta: DataDelta) {
                TODO("Not yet implemented")
            }

            override fun join(delta: MappingDelta): Stream<MappingDelta> {
                TODO("Not yet implemented")
            }

        }

        abstract val bindings: Set<String>

        abstract val cardinality: Cardinality

        abstract fun peek(delta: DataDelta): Stream<MappingDelta>

        abstract fun process(delta: DataDelta)

        abstract fun join(delta: MappingDelta): Stream<MappingDelta>

    }

    private val state = union.map { it.createIncrementalSegmentState() }

    override val bindings: Set<String> = buildSet { state.forEach { addAll(it.bindings) } }

    override val cardinality: Cardinality
        get() = Cardinality(state.sumOf { it.cardinality.toDouble() })

    override fun process(delta: DataDelta) {
        state.forEach { it.process(delta) }
    }

    override fun peek(delta: DataDelta): OptimisedStream<MappingDelta> {
        // whilst the max cardinality here is not correct in all cases, it covers most bases
        return state.toStream().transform(maxCardinality = 1) { it.peek(delta) }.optimisedForReuse()
    }

    override fun join(delta: MappingDelta): Stream<MappingDelta> {
        return state.toStream().transform(maxCardinality = state.maxOf { it.cardinality }) { s -> s.join(delta) }
    }

    companion object {

        /* helpers */

        private fun dev.tesserakt.sparql.runtime.incremental.types.Segment.createIncrementalSegmentState() = when (this) {
            is SelectQuerySegment -> Segment.SubqueryState(this)
            is StatementsSegment -> Segment.StatementsState(this)
        }
    }

}
