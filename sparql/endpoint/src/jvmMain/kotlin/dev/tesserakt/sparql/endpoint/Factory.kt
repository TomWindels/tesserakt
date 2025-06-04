package dev.tesserakt.sparql.endpoint

import dev.tesserakt.rdf.types.ObservableStore
import dev.tesserakt.rdf.types.factory.ObservableStore
import dev.tesserakt.sparql.endpoint.impl.SparqlEndpointImpl
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json


fun SparqlEndpoint(store: ObservableStore = ObservableStore()): SparqlEndpoint = SparqlEndpointImpl(store)

fun Application.sparqlEndpoint(
    slug: String = "sparql",
    store: ObservableStore = ObservableStore()
) {
    val endpoint = SparqlEndpoint(store)
    routing {
        post(slug) {
            when (call.request.contentType()) {
                SelectQueryType -> {
                    endpoint.onSelectQueryRequest(
                        query = call.receiveText()
                    ).fold(
                        onSuccess = { response ->
                            // we have to encode it ourselves so we can provide the custom response type
                            call.respondText(Json.encodeToString(response), ResponseMimeType)
                        },
                        onFailure = { cause ->
                            call.respond(
                                status = HttpStatusCode.BadRequest,
                                message = "Invalid query! Caught the following exception.\n${cause.message}"
                            )
                        }
                    )
                }
                UpdateQueryType -> {
                    endpoint.onUpdateQueryRequest(
                        query = call.receiveText()
                    ).fold(
                        onSuccess = {
                            call.respond(HttpStatusCode.OK)
                        },
                        onFailure = { cause ->
                            call.respond(
                                status = HttpStatusCode.BadRequest,
                                message = "Invalid query! Caught the following exception.\n${cause.message}"
                            )
                        }
                    )
                }
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
