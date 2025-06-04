package dev.tesserakt.sparql.endpoint

import dev.tesserakt.rdf.serialization.DelicateSerializationApi
import dev.tesserakt.rdf.serialization.common.deserialize
import dev.tesserakt.rdf.trig.serialization.TriGSerializer
import dev.tesserakt.rdf.types.ObservableStore
import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.rdf.types.Store
import dev.tesserakt.rdf.types.consume
import dev.tesserakt.rdf.types.factory.ObservableStore
import dev.tesserakt.rdf.types.factory.mutableStoreOf
import dev.tesserakt.sparql.*
import dev.tesserakt.sparql.evaluation.DeferredOngoingQueryEvaluation
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class Endpoint(
    private val config: EndpointConfig,
    private val store: ObservableStore = ObservableStore()
) {

    private val SelectQueryType = ContentType(contentType = "application", contentSubtype = "sparql-query")
    private val UpdateQueryType = ContentType(contentType = "application", contentSubtype = "sparql-update")
    private val ResponseMimeType = ContentType(contentType = "application", contentSubtype = "sparql-results+json")

    private val server = embeddedServer(Netty, port = config.port) {

        install(ContentNegotiation) {
            json()
        }

        routing {
            post(config.slug) {
                when (call.request.contentType()) {
                    SelectQueryType -> processSelectQuery()
                    UpdateQueryType -> processUpdateQuery()
                    else -> {
                        call.respond(
                            status = HttpStatusCode.BadRequest,
                            message = "Invalid Content-Type headers"
                        )
                    }
                }
            }
        }
    }

    fun run() {
        server.start(wait = true)
    }

    private val queryCache = mutableMapOf<Query<Bindings>, DeferredOngoingQueryEvaluation<Bindings>>()

    // https://www.w3.org/TR/2013/REC-sparql11-results-json-20130321/
    @Serializable
    private data class SelectResponse(
        val head: Head,
        val results: Results,
    ) {

        constructor(query: Query<Bindings>, evaluation: DeferredOngoingQueryEvaluation<Bindings>): this(
            head = Head(query),
            results = Results(evaluation)
        )

        @Serializable
        data class Head(
            @SerialName("vars")
            val variables: Collection<String>
        ) {
            constructor(query: Query<Bindings>): this(variables = query.variables)
        }

        @Serializable
        data class Results(
            val bindings: List<Map<String, Map<String, String>>>
        ) {
            constructor(evaluation: DeferredOngoingQueryEvaluation<Bindings>): this(
                bindings = evaluation.results.map { it.associate { it.first to it.second.encoded() } }
            )

            companion object {

                private fun Quad.Element.encoded(): Map<String, String> = when (this) {
                    is Quad.NamedTerm -> mapOf(
                        "type" to "uri",
                        "value" to value
                    )
                    is Quad.Literal -> mapOf(
                        "type" to "literal",
                        "value" to value,
                        "datatype" to type.value
                    )
                    is Quad.LangString -> mapOf(
                        "type" to "literal",
                        "value" to value,
                        "xml:lang" to language
                    )
                    is Quad.BlankTerm -> mapOf(
                        "type" to "bnode",
                        "value" to id.toString()
                    )
                    Quad.DefaultGraph -> mapOf(
                        "type" to "uri",
                        "value" to ""
                    )
                }

            }

        }

    }

    private suspend fun RoutingContext.processSelectQuery() {
        val raw = call.receiveText()
        val query = try {
            Query.Select(raw)
        } catch (s: SparqlException) {
            call.respond(
                status = HttpStatusCode.BadRequest,
                message = "Invalid query! Caught the following exception.\n${s.message}"
            )
            return
        }
        val evaluation = queryCache.getOrPut(query) { store.queryDeferred(query) }
        val data = SelectResponse(query, evaluation)
        try {
            call.respondText(Json.encodeToString(data), ResponseMimeType)
        } catch (e: Exception) {
            call.respond(e.stackTraceToString())
        }
    }

    private suspend fun RoutingContext.processUpdateQuery() {
        val raw = call.receiveText()
        val changes = try {
            extractChanges(raw)
        } catch (e: Exception) {
            call.respond(
                status = HttpStatusCode.BadRequest,
                message = "Invalid query! Caught the following exception.\n${e.message}"
            )
            return
        }
        if (changes.additions.isEmpty() && changes.deletions.isEmpty()) {
            call.respond(HttpStatusCode.NoContent)
            return
        }
        store.addAll(changes.additions)
        store.removeAll(changes.deletions)
        // small optimisation: if the UPDATE causes all data to be removed, it's much faster to clear
        //  any existing, outdated, queries and re-evaluate them from scratch than it is to
        //  first process all deletions from the outdated state
        if (store.isEmpty()) {
            queryCache.clear()
        }
        call.respond(HttpStatusCode.OK)
    }

    private data class Changes(
        val additions: Store,
        val deletions: Store,
    )

    @OptIn(DelicateSerializationApi::class)
    private fun extractChanges(body: String): Changes {
        // TODO improve the query processing with an actual parser
        val prefixes = Prefix
            .findAll(body)
            .map { it.value }
            .joinToString("\n", postfix = "\n")

        val additions = mutableStoreOf()
        InsertStructure.findAll(body).forEach {
            val data = prefixes + it.groupValues[1]
            TriGSerializer.deserialize(data).consume(additions)
        }
        val deletions = mutableStoreOf()
        DeleteStructure.findAll(body).forEach {
            val data = prefixes + it.groupValues[1]
            TriGSerializer.deserialize(data).consume(deletions)
        }
        return Changes(
            additions = additions,
            deletions = deletions
        )
    }

    private val Prefix = Regex("PREFIX\\s+[^:]+:\\s*<[^>]+>", RegexOption.IGNORE_CASE)

    private val InsertStructure = Regex("INSERT\\s+DATA\\s*\\{((?:\\s*GRAPH[^{]*\\{[^}]+}|[^{}]*)*)}", RegexOption.IGNORE_CASE)

    private val DeleteStructure = Regex("DELETE\\s+DATA\\s*\\{((?:\\s*GRAPH[^{]*\\{[^}]+}|[^{}]*)*)}", RegexOption.IGNORE_CASE)

}
