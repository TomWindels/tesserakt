package dev.tesserakt.rdf.serialization.common

import dev.tesserakt.rdf.ontology.Ontology
import dev.tesserakt.rdf.types.Quad
import kotlin.jvm.JvmInline


@JvmInline
value class Prefixes(private val map: Map<String /* prefix */, String /* uri */>): Collection<Map.Entry<String, String>> {

    data class PrefixedTerm(
        val prefix: String,
        val value: String,
    )

    constructor(vararg ontology: Ontology): this(map = ontology.associate { it.prefix to it.base_uri })

    fun format(term: Quad.NamedTerm): PrefixedTerm? {
        map.forEach { (prefix, uri) ->
            // FIXME needs more accurate testing, i.e. remainder does not contain `/` etc
            if (term.value.startsWith(uri)) {
                return PrefixedTerm(prefix, term.value.drop(uri.length))
            }
        }
        return null
    }

    override fun iterator(): Iterator<Map.Entry<String, String>> {
        return map.iterator()
    }

    override val size: Int
        get() = map.size

    override fun containsAll(elements: Collection<Map.Entry<String, String>>) =
        map.entries.containsAll(elements)

    override fun contains(element: Map.Entry<String, String>) = map.entries.contains(element)

    override fun isEmpty() = map.isEmpty()

    operator fun plus(other: Prefixes) = Prefixes(map + other.map)

    companion object {

        operator fun Map<String, String>.plus(ontology: Ontology) = this.plus(ontology.prefix to ontology.base_uri)

    }

}
