package dev.tesserakt.sparql.runtime.incremental.state

import dev.tesserakt.sparql.runtime.incremental.delta.*
import dev.tesserakt.sparql.runtime.incremental.types.Patterns
import kotlin.jvm.JvmInline

@JvmInline
internal value class IncrementalPatternsState(private val state: JoinTree): MutableJoinState by state {

    constructor(ast: Patterns): this(JoinTree(ast))

    fun peek(delta: DataAddition): List<MappingAddition> {
        // we can guarantee it in this case
        @Suppress("UNCHECKED_CAST")
        return state.peek(delta) as List<MappingAddition>
    }

    fun peek(delta: DataDeletion): List<MappingDeletion> {
        // we can guarantee it in this case
        @Suppress("UNCHECKED_CAST")
        return state.peek(delta) as List<MappingDeletion>
    }

    fun join(delta: List<MappingDelta>): List<MappingDelta> {
        return state.join(delta)
    }

    fun debugInformation() = state.debugInformation()

}
