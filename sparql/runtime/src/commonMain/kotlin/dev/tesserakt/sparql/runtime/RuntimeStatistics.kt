package dev.tesserakt.sparql.runtime

object RuntimeStatistics {

    private data class State(
        var arrayBackedJoin: Int = 0,
        var joinTreeHits: Int = 0,
        var joinTreeMisses: Int = 0,
        var joinTreeResults: Int = 0,
        val extra: MutableList<String> = mutableListOf()
    )

    fun reset() {
        state = State()
    }

    fun report() = buildString {
        appendLine("Debug information")
        appendLine("\tArray-backed pattern joins: ${state.arrayBackedJoin}")
        appendLine("\tJoin tree hits: ${state.joinTreeHits}")
        appendLine("\tJoin tree misses: ${state.joinTreeMisses}")
        if (state.extra.isEmpty()) {
            append("\tJoin tree results: ${state.joinTreeResults}")
        } else {
            appendLine("\tJoin tree results: ${state.joinTreeResults}")
            repeat(state.extra.size - 1) {
                appendLine(state.extra[it])
            }
            append(state.extra.last())
        }
    }

    private var state = State()

    fun onArrayPatternJoinExecuted() {
        state.arrayBackedJoin += 1
    }

    fun onJoinTreeHit(count: Int) {
        state.joinTreeHits += 1
        state.joinTreeResults += count
    }

    fun onJoinTreeMiss() {
        state.joinTreeMisses += 1
    }

    fun append(extra: String) {
        state.extra.add(extra)
    }

}
