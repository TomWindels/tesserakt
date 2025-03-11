package dev.tesserakt.sparql.types.runtime.evaluation

import dev.tesserakt.rdf.types.Quad
import kotlin.jvm.JvmName

internal class Mapping(private val inner: Map<String, Quad.Term>): Map<String, Quad.Term> by inner {
    // caching this thing, guaranteed to be static
    private val hash = inner.hashCode()

    val bindings: Bindings get() = inner

    override fun hashCode() = hash

    override fun equals(other: Any?): Boolean = other is Mapping && hash == other.hash && inner == other.inner

    operator fun plus(other: Mapping): Mapping = Mapping(inner = this.inner + other.inner)

    override fun toString() = bindings.toString()

}

internal val EmptyMapping = Mapping(emptyMap())

internal fun mappingOf(vararg pairs: Pair<String, Quad.Term>): Mapping =
    Mapping(inner = HashMap<String, Quad.Term>(pairs.size).also { it.putAll(pairs) })

@JvmName("mappingOfNullable")
internal fun mappingOf(vararg pairs: Pair<String?, Quad.Term>): Mapping = HashMap<String, Quad.Term>(pairs.size)
    .also { map ->
        pairs.forEach { (first, second) ->
            if (first != null) {
                map[first] = second
            }
        }
    }.toMapping()

internal fun Bindings.toMapping() = Mapping(inner = this)

internal fun emptyMapping(): Mapping = EmptyMapping
