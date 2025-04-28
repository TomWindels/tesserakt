package dev.tesserakt.rdf.serialization.common

import dev.tesserakt.rdf.serialization.DelicateSerializationApi
import java.io.InputStream

actual sealed interface Source {
    actual class File(val file: java.io.File) : Source {
        actual constructor(filepath: String): this(file = java.io.File(filepath))
    }

    @JvmInline
    value class Stream(val stream: InputStream) : Source

    @DelicateSerializationApi
    @JvmInline
    actual value class Text(val text: String) : Source
}
