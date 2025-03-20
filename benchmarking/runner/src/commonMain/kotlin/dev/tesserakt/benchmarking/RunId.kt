package dev.tesserakt.benchmarking


data class RunId(
    val queryIndex: Int,
    val deltaIndex: Int,
    val runIndex: Int,
) {
    fun id() = "#${queryIndex} Δ${deltaIndex}"
    override fun toString() = "#${queryIndex} Δ${deltaIndex} (${runIndex})"
}
