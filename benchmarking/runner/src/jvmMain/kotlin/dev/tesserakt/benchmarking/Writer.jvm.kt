package dev.tesserakt.benchmarking

import java.io.BufferedWriter
import java.io.FileWriter

actual class Writer actual constructor(filepath: String) {

    private val writer = BufferedWriter(FileWriter(filepath))

    actual fun write(text: String) {
        writer.write(text)
    }

    actual fun close() {
        writer.close()
    }

}
