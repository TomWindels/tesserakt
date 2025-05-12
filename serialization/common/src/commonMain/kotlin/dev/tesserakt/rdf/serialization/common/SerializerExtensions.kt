package dev.tesserakt.rdf.serialization.common

import dev.tesserakt.rdf.serialization.DelicateSerializationApi
import dev.tesserakt.rdf.types.Quad

/**
 * A helper to streamline deserialization from text.
 */
@DelicateSerializationApi
fun Serializer.deserialize(input: String): Iterator<Quad> {
    return deserialize(input = TextDataSource(text = input))
}

fun Iterator<String>.collect(): String = buildString {
    this@collect.forEach { segment -> append(segment) }
}
