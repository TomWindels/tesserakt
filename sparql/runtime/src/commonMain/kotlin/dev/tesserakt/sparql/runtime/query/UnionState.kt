package dev.tesserakt.sparql.runtime.query

import dev.tesserakt.sparql.ast.GraphPatternSegment
import dev.tesserakt.sparql.ast.Segment
import dev.tesserakt.sparql.ast.SelectQuerySegment
import dev.tesserakt.sparql.ast.Union
import dev.tesserakt.sparql.runtime.evaluation.DataDelta
import dev.tesserakt.sparql.runtime.evaluation.MappingDelta
import dev.tesserakt.sparql.runtime.stream.*
import dev.tesserakt.sparql.util.Cardinality

class UnionState(union: Union): MutableJoinState {

    private sealed class Segment {

        class GraphPatternSegmentState(parent: GraphPatternSegment): Segment() {

            private val state = BasicGraphPatternState(parent.pattern)

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

            override val bindings: Set<String> = parent.query.bindings

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

        private fun dev.tesserakt.sparql.ast.Segment.createIncrementalSegmentState() = when (this) {
            is SelectQuerySegment -> Segment.SubqueryState(this)
            is GraphPatternSegment -> Segment.GraphPatternSegmentState(this)
        }
    }

}
