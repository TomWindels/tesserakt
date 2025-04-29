package dev.tesserakt.rdf.serialization.common

import dev.tesserakt.rdf.serialization.DelicateSerializationApi
import dev.tesserakt.rdf.types.Quad

fun <C> ConfigurableSerializer<C>.serialize(data: Iterator<Quad>, config: C.() -> Unit): Iterator<String> {
    val c = DEFAULT.apply(config)
    return serialize(data = data, config = c)
}

fun <C> ConfigurableSerializer<C>.serialize(data: Collection<Quad>, config: C.() -> Unit): Iterator<String> {
    val c = DEFAULT.apply(config)
    return serialize(data = data, config = c)
}

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
