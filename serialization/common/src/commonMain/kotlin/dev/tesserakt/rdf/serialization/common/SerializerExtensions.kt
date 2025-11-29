package dev.tesserakt.rdf.serialization.common

import dev.tesserakt.rdf.serialization.DelicateSerializationApi

/**
 * Deserializes the [input] [String] directly. Not recommended as the serialized representation may be large.
 */
@DelicateSerializationApi
fun Serializer.deserialize(input: String) = deserialize(input = TextDataSource(text = input))
