package dev.tesserakt.sparql.runtime.incremental.state

import dev.tesserakt.sparql.runtime.incremental.delta.Delta
import dev.tesserakt.sparql.runtime.incremental.types.Patterns

class IncrementalPatternsState(ast: Patterns) {

    private val state = JoinTree(ast)

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

    fun peek(delta: Delta.Data): List<Delta.Bindings> {
        return state.peek(delta)
    }

    fun process(delta: Delta.Data) {
        return state.process(delta)
    }

    fun join(delta: Delta.Bindings): List<Delta.Bindings> {
        return state.join(delta)
    }

    fun join(delta: List<Delta.Bindings>): List<Delta.Bindings> {
        return state.join(delta)
    }

    fun debugInformation() = state.debugInformation()

}
