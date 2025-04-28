package dev.tesserakt.rdf.serialization.common

import dev.tesserakt.rdf.serialization.DelicateSerializationApi

actual sealed interface Source {
    actual class File actual constructor(val filepath: String) : Source

    @DelicateSerializationApi
    actual value class Text(val text: String) : Source
}
