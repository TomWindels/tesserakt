package dev.tesserakt.sparql.endpoint

import dev.tesserakt.rdf.types.ObservableStore
import dev.tesserakt.rdf.types.factory.ObservableStore
import dev.tesserakt.sparql.Bindings
import dev.tesserakt.sparql.Query
import dev.tesserakt.sparql.SparqlException
import dev.tesserakt.sparql.endpoint.data.SelectResponse
import dev.tesserakt.sparql.endpoint.data.UpdateRequest.Companion.receiveUpdateQuery
import dev.tesserakt.sparql.evaluation.DeferredOngoingQueryEvaluation
import dev.tesserakt.sparql.queryDeferred
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json

class Endpoint(
    private val config: EndpointConfig,
    private val store: ObservableStore = ObservableStore()
) {

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
        // we have to encode it ourselves so we can provide the custom response type
        call.respondText(Json.encodeToString(data), ResponseMimeType)
    }

    private suspend fun RoutingContext.processUpdateQuery() {
        val request = try {
            call.receiveUpdateQuery()
        } catch (e: Exception) {
            call.respond(
                status = HttpStatusCode.BadRequest,
                message = "Invalid query! Caught the following exception.\n${e.message}"
            )
            return
        }
        if (request.additions.isEmpty() && request.deletions.isEmpty()) {
            call.respond(HttpStatusCode.NoContent)
            return
        }
        store.addAll(request.additions)
        store.removeAll(request.deletions)
        // small optimisation: if the UPDATE causes all data to be removed, it's much faster to clear
        //  any existing, outdated, queries and re-evaluate them from scratch than it is to
        //  first process all deletions from the outdated state
        if (store.isEmpty()) {
            queryCache.clear()
        }
        call.respond(HttpStatusCode.OK)
    }

}
