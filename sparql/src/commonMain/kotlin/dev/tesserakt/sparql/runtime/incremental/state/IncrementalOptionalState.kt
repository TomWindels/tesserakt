package dev.tesserakt.sparql.runtime.incremental.state

import dev.tesserakt.sparql.runtime.core.Mapping
import dev.tesserakt.sparql.runtime.incremental.delta.*
import dev.tesserakt.sparql.runtime.incremental.types.Optional
import dev.tesserakt.sparql.runtime.incremental.types.Query
import dev.tesserakt.sparql.runtime.util.getAllBindings

internal class IncrementalOptionalState(parent: Query.QueryBody, ast: Optional): MutableJoinState {

    private val state = IncrementalPatternsState(ast.patterns)

    override val bindings: Set<String>
        get() = state.bindings

    // extracting of our own bindings not visible to the parent, required for accurate deltas during `peek()`
    private val invisible: Set<String> = run {
        val ours = ast.patterns.getAllBindings()
        val parents = parent.patterns.getAllBindings() + parent.unions.flatMapTo(mutableSetOf()) { union -> union.segments.flatMap { it.getAllBindings() } }
        (ours - parents).mapTo(mutableSetOf()) { it.name }
    }

    override fun peek(delta: DataDelta): List<MappingDelta> {
        when (delta) {
            is DataAddition -> {
                // with the data change, it's possible we transition from an unsatisfied optional to a satisfied one,
                //  meaning we have to remove the unsatisfied no-bindings results first with an appropriate deletion call
                val peeked = state.peek(delta)
                // duping what we peeked, first requesting them to be removed (non-internal bindings)
                //  (subset of bindings visible to the parent query body), and then added back,
                //  but only if we never matched with it first
                val deletions = peeked.map { MappingDeletion(Mapping(it.value - invisible), origin = null) }
                return deletions + peeked
            }
            is DataDeletion -> {
                // with the data change, it's possible we transition from a satisfied optional to an unsatisfied one,
                //  meaning we have to remove the satisfied bindings results first with an appropriate deletion call
                val peeked = state.peek(delta)
                // duping what we peeked, first requesting them to be removed (non-internal bindings)
                //  (subset of bindings visible to the parent query body), and then added back,
                //  but only if we never matched with it first
                val additions = peeked.map { MappingAddition(Mapping(it.value - invisible), origin = null) }
                return peeked + additions
            }
        }
    }

    override fun process(delta: DataDelta) {
        // simply propagating it downwards
        state.process(delta)
    }

    override fun join(delta: MappingDelta): List<MappingDelta> {
        // joining as is typical, but propagating the original delta if there's nothing compatible to join with, as
        //  we're optional here
        return state.join(delta).ifEmpty { listOf(delta) }
    }

    override fun debugInformation(): String {
        return state.debugInformation()
    }

}
