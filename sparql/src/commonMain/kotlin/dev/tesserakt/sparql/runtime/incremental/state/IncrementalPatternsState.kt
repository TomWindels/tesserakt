package dev.tesserakt.sparql.runtime.incremental.state

import dev.tesserakt.sparql.runtime.incremental.delta.Delta
import dev.tesserakt.sparql.runtime.incremental.types.DebugWriter
import dev.tesserakt.sparql.runtime.incremental.types.Patterns
import dev.tesserakt.sparql.runtime.util.getAllBindings

internal class IncrementalPatternsState(ast: Patterns): MutableJoinState {

    private val state = JoinTree(ast)

    override val bindings: Set<String> = ast.getAllBindings().mapTo(mutableSetOf()) { it.name }

    fun peek(delta: Delta.DataAddition): List<Delta.BindingsAddition> {
        // we can guarantee it in this case
        @Suppress("UNCHECKED_CAST")
        return state.peek(delta) as List<Delta.BindingsAddition>
    }

    fun peek(delta: Delta.DataDeletion): List<Delta.BindingsDeletion> {
        // we can guarantee it in this case
        @Suppress("UNCHECKED_CAST")
        return state.peek(delta) as List<Delta.BindingsDeletion>
    }

    override fun peek(delta: Delta.Data): List<Delta.Bindings> {
        return state.peek(delta)
    }

    override fun process(delta: Delta.Data) {
        return state.process(delta)
    }

    override fun join(delta: Delta.Bindings): List<Delta.Bindings> {
        return state.join(delta)
    }

    fun join(delta: List<Delta.Bindings>): List<Delta.Bindings> {
        return state.join(delta)
    }

    override fun debugInformation(writer: DebugWriter) = state.debugInformation(writer)

}
