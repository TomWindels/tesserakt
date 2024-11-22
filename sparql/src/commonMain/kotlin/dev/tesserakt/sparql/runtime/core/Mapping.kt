package dev.tesserakt.sparql.runtime.core

import dev.tesserakt.rdf.types.Quad
import kotlin.jvm.JvmName

typealias Mapping = Map<String, Quad.Term>

fun mappingOf(vararg pairs: Pair<String, Quad.Term>): Mapping = HashMap<String, Quad.Term>(pairs.size)
    .also { it.putAll(pairs) }

@JvmName("mappingOfNullable")
fun mappingOf(vararg pairs: Pair<String?, Quad.Term>): Mapping = HashMap<String, Quad.Term>(pairs.size)
    .also { map ->
        pairs.forEach { (first, second) ->
            if (first != null) {
                map[first] = second
            }
        }
    }

fun emptyMapping(): Mapping = emptyMap()
