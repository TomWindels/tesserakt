package dev.tesserakt.benchmarking


data class RunId(
    val deltaIndex: Int,
) {
    fun id() = "Δ${deltaIndex}"
    override fun toString() = "Δ${deltaIndex}"
}
