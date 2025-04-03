package dev.tesserakt.benchmarking

expect class Writer(filepath: String) {
    fun write(text: String)
    fun close()
}
