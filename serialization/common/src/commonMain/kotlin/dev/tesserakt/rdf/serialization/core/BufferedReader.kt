package dev.tesserakt.rdf.serialization.core

import dev.tesserakt.rdf.serialization.common.Source

expect class BufferedReader: AutoCloseable {
    override fun close()
}

expect fun BufferedReader.read(count: Int): String?

expect fun Source.open(): BufferedReader
