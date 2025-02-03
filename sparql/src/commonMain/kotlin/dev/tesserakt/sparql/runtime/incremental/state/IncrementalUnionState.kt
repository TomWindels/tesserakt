package dev.tesserakt.sparql.runtime.incremental.state

import dev.tesserakt.sparql.runtime.incremental.delta.Delta
import dev.tesserakt.sparql.runtime.incremental.types.DebugWriter
import dev.tesserakt.sparql.runtime.incremental.types.SelectQuerySegment
import dev.tesserakt.sparql.runtime.incremental.types.StatementsSegment
import dev.tesserakt.sparql.runtime.incremental.types.Union

internal class IncrementalUnionState(union: Union): MutableJoinState {

    private sealed class Segment {

        class StatementsState(parent: StatementsSegment): Segment() {

            private val state = IncrementalBasicGraphPatternState(parent.statements)

            override val bindings: Set<String> get() = state.bindings

            override fun peek(delta: Delta.Data): List<Delta.Bindings> {
                return state.peek(delta)
            }

            override fun process(delta: Delta.Data) {
                return state.process(delta)
            }

            override fun join(delta: Delta.Bindings): List<Delta.Bindings> {
                return state.join(delta)
            }

            override fun debugInformation(writer: DebugWriter) {
                return state.debugInformation(writer)
            }

        }

        class SubqueryState(parent: SelectQuerySegment): Segment() {

            override val bindings: Set<String> = parent.query.output.map { it.name }.toSet()

            override fun peek(delta: Delta.Data): List<Delta.Bindings> {
                TODO("Not yet implemented")
            }

            override fun process(delta: Delta.Data) {
                TODO("Not yet implemented")
            }

            override fun join(delta: Delta.Bindings): List<Delta.Bindings> {
                TODO("Not yet implemented")
            }

            override fun debugInformation(writer: DebugWriter) {
                TODO("Not yet implemented")
            }

        }

        abstract val bindings: Set<String>

        abstract fun peek(delta: Delta.Data): List<Delta.Bindings>

        abstract fun process(delta: Delta.Data)

        abstract fun join(delta: Delta.Bindings): List<Delta.Bindings>

        abstract fun debugInformation(writer: DebugWriter)

    }

    private val state = union.map { it.createIncrementalSegmentState() }

    override val bindings: Set<String> = buildSet { state.forEach { addAll(it.bindings) } }

    override fun process(delta: Delta.Data) {
        state.forEach { it.process(delta) }
    }

    override fun peek(delta: Delta.Data): List<Delta.Bindings> {
        return state.flatMap { it.peek(delta) }
    }

    override fun join(delta: Delta.Bindings): List<Delta.Bindings> {
        return state.flatMap { s -> s.join(delta) }
    }

    override fun debugInformation(writer: DebugWriter) {
        writer.block(" * ") {
            appendLine("Union state")
            state.forEachIndexed { i, segment ->
                writer.block(" > ") {
                    appendLine("Segment ${i + 1}")
                    segment.debugInformation(writer)
                }
            }
        }
    }

    companion object {

        /* helpers */

        private fun dev.tesserakt.sparql.runtime.incremental.types.Segment.createIncrementalSegmentState() = when (this) {
            is SelectQuerySegment -> Segment.SubqueryState(this)
            is StatementsSegment -> Segment.StatementsState(this)
        }
    }

}
