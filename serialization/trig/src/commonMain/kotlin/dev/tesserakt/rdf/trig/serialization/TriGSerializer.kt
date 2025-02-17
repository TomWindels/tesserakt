package dev.tesserakt.rdf.trig.serialization

import dev.tesserakt.rdf.serialization.common.Prefixes
import dev.tesserakt.rdf.types.Quad

object TriGSerializer {

    val NoPrefixes = Prefixes(emptyMap())

    fun serialize(
        store: Collection<Quad>,
        prefixes: Prefixes = NoPrefixes,
    ): String {
        val formatter = PrettyFormatter(prefixes)
        val serializer = TokenEncoder(store)
        return serializer.iterator().writeToString(formatter)
    }

    fun serialize(
        store: Collection<Quad>,
        prefixes: Map<String, String>,
    ): String {
        val formatter = PrettyFormatter(Prefixes(prefixes))
        val serializer = TokenEncoder(store)
        return serializer.iterator().writeToString(formatter)
    }

    inline fun serialize(
        store: Collection<Quad>,
        prefixes: Map<String, String>,
        callback: (String) -> Unit
    ) {
        serialize(store, PrettyFormatter(Prefixes(prefixes))).forEach(callback)
    }

    fun serialize(
        store: Collection<Quad>,
        formatter: Formatter,
    ): Iterator<String> {
        return formatter.format(TokenEncoder(store).iterator())
    }

    private fun Iterator<TriGToken>.writeToString(formatter: Formatter): String {
        val result = StringBuilder()
        formatter
            .format(this)
            .forEach { text -> result.append(text) }
        return result.toString()
    }

}
