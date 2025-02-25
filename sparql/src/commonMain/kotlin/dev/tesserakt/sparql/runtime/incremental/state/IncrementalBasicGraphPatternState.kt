package dev.tesserakt.sparql.runtime.incremental.state

import dev.tesserakt.sparql.runtime.incremental.delta.DataDelta
import dev.tesserakt.sparql.runtime.incremental.delta.MappingDelta
import dev.tesserakt.sparql.runtime.incremental.types.Query
import kotlin.jvm.JvmInline

@JvmInline
internal value class IncrementalBasicGraphPatternState(private val state: MutableJoinState) {

    constructor(ast: Query.QueryBody): this(
        state = IncrementalOptionalStateTest.from(
            inner = JoinTree(
                IncrementalPatternsState(ast.patterns),
                JoinTree(ast.unions),
            ),
            optionals = ast.optional
        )
    )

    /**
     * A collection of all bindings found inside this query body; it is not guaranteed that all solutions generated
     *  through [insert]ion have a value for all of these bindings, as this depends on the query itself
     */
    val bindings: Set<String>
        get() = state.bindings

    fun insert(delta: DataDelta): List<MappingDelta> {
        val total = peek(delta)
        process(delta)
        return total
    }

    fun peek(delta: DataDelta): List<MappingDelta> {
        return state.peek(delta)
    }

    fun process(delta: DataDelta) {
        state.process(delta)
    }

    fun join(delta: MappingDelta): List<MappingDelta> {
        return state.join(delta)
    }

    fun debugInformation() = buildString {
        appendLine("* Pattern state")
        append(state.debugInformation())
    }

}
