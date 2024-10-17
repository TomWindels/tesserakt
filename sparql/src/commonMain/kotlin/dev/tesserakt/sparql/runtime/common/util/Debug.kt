package dev.tesserakt.sparql.runtime.common.util

object Debug {

    data class State(
        var arrayBackedJoin: Int = 0,
    )

    fun reset() {
        state = State()
    }

    fun report() = buildString {
        appendLine("Debug information")
        appendLine("\tArray-backed pattern joins: ${state.arrayBackedJoin}")
    }

    private var state = State()

    internal fun onArrayPatternJoinExecuted() {
        state.arrayBackedJoin += 1
    }

}
