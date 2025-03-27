package dev.tesserakt.benchmarking

import java.io.BufferedWriter
import java.io.FileWriter

class OutputObserver(filepath: String) {

    private val writer = BufferedWriter(FileWriter(filepath))

    init {
        writer.write("name,additions,removals,checksum\n")
    }

    fun markResult(id: String, output: Evaluator.Output) {
        writer.write("$id,${output.added},${output.removed},${output.checksum}\n")
    }

    fun stop() {
        writer.close()
    }

}
