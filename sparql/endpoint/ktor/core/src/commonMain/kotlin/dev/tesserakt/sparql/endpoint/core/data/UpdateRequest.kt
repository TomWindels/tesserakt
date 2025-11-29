package dev.tesserakt.sparql.endpoint.core.data

import dev.tesserakt.rdf.serialization.DelicateSerializationApi
import dev.tesserakt.rdf.serialization.common.deserialize
import dev.tesserakt.rdf.serialization.common.serializer
import dev.tesserakt.rdf.trig.serialization.TriG
import dev.tesserakt.rdf.types.Store
import dev.tesserakt.rdf.types.factory.mutableStoreOf
import dev.tesserakt.rdf.types.toStore

data class UpdateRequest(
    val additions: Store,
    val deletions: Store,
) {

    companion object {

        @OptIn(DelicateSerializationApi::class)
        fun parse(query: String): UpdateRequest {
            // TODO improve the query processing with an actual parser
            val prefixes = Prefix
                .findAll(query)
                .map { it.value }
                .joinToString("\n", postfix = "\n")

            val additions = mutableStoreOf()
            InsertStructure.findAll(query).forEach {
                val data = prefixes + it.groupValues[1]
                serializer(TriG).deserialize(data).toStore(additions)
            }
            val deletions = mutableStoreOf()
            DeleteStructure.findAll(query).forEach {
                val data = prefixes + it.groupValues[1]
                serializer(TriG).deserialize(data).toStore(deletions)
            }
            return UpdateRequest(
                additions = additions,
                deletions = deletions
            )
        }

        private val Prefix = Regex("PREFIX\\s+[^:]+:\\s*<[^>]+>", RegexOption.IGNORE_CASE)

        private val InsertStructure = Regex("INSERT\\s+DATA\\s*\\{((?:\\s*GRAPH[^{]*\\{[^}]+}|[^{}]*)*)}", RegexOption.IGNORE_CASE)

        private val DeleteStructure = Regex("DELETE\\s+DATA\\s*\\{((?:\\s*GRAPH[^{]*\\{[^}]+}|[^{}]*)*)}", RegexOption.IGNORE_CASE)

    }

}
