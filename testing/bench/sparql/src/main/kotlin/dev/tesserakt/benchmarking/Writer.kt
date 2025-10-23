package dev.tesserakt.benchmarking

import java.io.BufferedWriter
import java.io.FileWriter

class Writer(filepath: String) {

    private val writer = BufferedWriter(FileWriter(filepath))

    fun write(text: String) {
        writer.write(text)
    }

    fun close() {
        writer.close()
    }

}
