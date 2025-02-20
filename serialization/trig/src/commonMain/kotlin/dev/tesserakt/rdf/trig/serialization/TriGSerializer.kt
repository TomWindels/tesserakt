package dev.tesserakt.rdf.trig.serialization

import dev.tesserakt.rdf.serialization.common.Path
import dev.tesserakt.rdf.serialization.common.Prefixes
import dev.tesserakt.rdf.serialization.common.open
import dev.tesserakt.rdf.types.Quad

object TriGSerializer {

    /* serialization API (and internals) */

    val NoPrefixes = Prefixes(emptyMap())

    fun serialize(
        data: Iterable<Quad>,
        prefixes: Prefixes = NoPrefixes,
    ): String {
        val formatter = PrettyFormatter(prefixes)
        val serializer = TokenEncoder(data)
        return serializer.iterator().writeToString(formatter)
    }

    fun serialize(
        data: Iterable<Quad>,
        prefixes: Map<String, String>,
    ): String {
        val formatter = PrettyFormatter(Prefixes(prefixes))
        val serializer = TokenEncoder(data)
        return serializer.iterator().writeToString(formatter)
    }

    inline fun serialize(
        data: Iterable<Quad>,
        prefixes: Map<String, String>,
        callback: (String) -> Unit
    ) {
        serialize(data, PrettyFormatter(Prefixes(prefixes))).forEach(callback)
    }

    fun serialize(
        data: Iterable<Quad>,
        formatter: Formatter,
    ): Iterator<String> {
        return formatter.format(TokenEncoder(data).iterator())
    }

    fun serialize(
        data: Iterator<Quad>,
        formatter: Formatter,
    ): Iterator<String> {
        return formatter.format(TokenEncoder(data).iterator())
    }

    private fun Iterator<TriGToken>.writeToString(formatter: Formatter): String {
        val result = StringBuilder()
        formatter
            .format(this)
            .forEach { text -> result.append(text) }
        return result.toString()
    }

    /* deserialization API (and internals) */

    inline fun deserialize(
        path: Path,
        consumer: (Quad) -> Unit
    ) {
        deserialize(path).forEach(consumer)
    }

    fun deserialize(path: Path): Iterator<Quad> {
        return Deserializer(TokenDecoder(path.open().getOrThrow()))
    }

}
