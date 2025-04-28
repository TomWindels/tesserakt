package dev.tesserakt.rdf.serialization.common

import dev.tesserakt.rdf.serialization.DelicateSerializationApi
import kotlin.jvm.JvmInline


expect sealed interface Source {
    @DelicateSerializationApi
    @JvmInline
    value class Text(val text: String) : Source

    class File(filepath: String) : Source
}
