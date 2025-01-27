package dev.tesserakt.sparql.runtime.incremental.state

import dev.tesserakt.sparql.runtime.incremental.delta.Delta
import dev.tesserakt.sparql.runtime.incremental.types.Optional
import dev.tesserakt.sparql.runtime.incremental.types.Query
import dev.tesserakt.sparql.runtime.util.getAllBindings
import dev.tesserakt.sparql.runtime.util.getAllNamedBindings

class IncrementalOptionalState(parent: Query.QueryBody, ast: Optional): MutableJoinState {

    private val state = IncrementalPatternsState(ast.patterns)

    override val bindings: Set<String> = ast.patterns
        .getAllNamedBindings()
        .mapTo(mutableSetOf()) { it.name }

    // extracting of our own bindings not visible to the parent, required for accurate deltas during `peek()`
    private val internalBindings: Set<String> = run {
        val ours = ast.patterns.getAllBindings()
        val parents = parent.patterns.getAllBindings() + parent.unions.flatMapTo(mutableSetOf()) { it.segments.flatMap { it.getAllBindings() } }
        (ours - parents).mapTo(mutableSetOf()) { it.name }
    }

    init {
        check(internalBindings.isNotEmpty()) {
            "Invalid optional state! Optionals with no unique bindings have no effect, and should be optimised away!\nProblematic optional: $ast"
        }
    }

    override fun peek(delta: Delta.Data): List<Delta.Bindings> {
        when (delta) {
            is Delta.DataAddition -> {
                // with the data change, it's possible we transition from an unsatisfied optional to a satisfied one,
                //  meaning we have to remove the unsatisfied no-bindings results first with an appropriate deletion call
                val peeked = state.peek(delta)
                // duping what we peeked, first requesting them to be removed (non-internal bindings)
                //  (subset of bindings visible to the parent query body), and then added back
                val deletions = peeked.map { Delta.BindingsDeletion(it.value - internalBindings) }
                return deletions + peeked
            }
            is Delta.DataDeletion -> {
                // with the data change, it's possible we transition from a satisfied optional to an unsatisfied one,
                //  meaning we have to remove the satisfied bindings results first with an appropriate deletion call
                val peeked = state.peek(delta)
                // duping what we peeked, first requesting them to be removed (non-internal bindings)
                //  (subset of bindings visible to the parent query body), and then added back
                val additions = peeked.map { Delta.BindingsAddition(it.value - internalBindings) }
                return peeked + additions
            }
        }
    }

    override fun process(delta: Delta.Data) {
        // simply propagating it downwards
        state.process(delta)
    }

    override fun join(delta: Delta.Bindings): List<Delta.Bindings> {
        // joining as is typical, but propagating the original delta if there's nothing compatible to join with, as
        //  we're optional here
        return state.join(delta).ifEmpty { listOf(delta) }
    }

}
