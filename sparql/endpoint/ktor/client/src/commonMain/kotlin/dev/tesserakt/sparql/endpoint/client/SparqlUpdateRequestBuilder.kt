package dev.tesserakt.sparql.endpoint.client

import dev.tesserakt.rdf.serialization.common.serializer
import dev.tesserakt.rdf.trig.serialization.TriG
import dev.tesserakt.rdf.types.MutableStore
import dev.tesserakt.rdf.types.factory.mutableStoreOf


/**
 * Intermediate builder type, used to construct SPARQL UPDATE queries.
 */
class SparqlUpdateRequestBuilder(
    internal val additions: MutableStore = mutableStoreOf(),
    internal val deletions: MutableStore = mutableStoreOf(),
) {
    fun optimise(): SparqlUpdateRequestBuilder {
        val common = additions.intersect(deletions)
        if (common.isEmpty()) {
            return this
        }
        additions.removeAll(common)
        deletions.removeAll(common)
        return this
    }

    fun toQueryString(): String = buildString {
        if (additions.isNotEmpty()) {
            append("INSERT DATA { ")
            serializer(TriG).serialize(additions).forEach { append(it) }
            append(" }")
        }
        if (additions.isNotEmpty() && deletions.isNotEmpty()) {
            append(";\n")
        }
        if (deletions.isNotEmpty()) {
            append("DELETE DATA { ")
            serializer(TriG).serialize(deletions).forEach { append(it) }
            append(" }")
        }
    }
}
