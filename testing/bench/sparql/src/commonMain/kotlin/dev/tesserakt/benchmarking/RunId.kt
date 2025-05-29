package dev.tesserakt.benchmarking


data class RunId(
    val deltaIndex: Int,
    val runIndex: Int,
) {
    fun id() = "Δ${deltaIndex}"
    override fun toString() = "Δ${deltaIndex} (${runIndex})"
}
