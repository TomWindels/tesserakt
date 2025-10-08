package dev.tesserakt.benchmarking

import dev.tesserakt.benchmarking.endpoint.EndpointEvaluator

class OutputObserver(filepath: String) {

    private val writer = Writer(filepath)

    init {
        writer.write("name,additions,removals,checksum\n")
    }

    fun markResult(id: String, output: EndpointEvaluator.Output) {
        writer.write("$id,${output.added},${output.removed},${output.checksum}\n")
    }

    fun stop() {
        writer.close()
    }

}
