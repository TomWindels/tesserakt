package dev.tesserakt.sparql.endpoint.server

import dev.tesserakt.rdf.types.ObservableStore
import dev.tesserakt.rdf.types.factory.ObservableStore
import dev.tesserakt.sparql.endpoint.core.SparqlContentType
import dev.tesserakt.sparql.endpoint.core.data.SelectResponse
import dev.tesserakt.sparql.endpoint.server.impl.SparqlEndpoint
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream


fun Application.sparqlEndpoint(
    slug: String = "sparql",
    store: ObservableStore = ObservableStore(),
    json: Json = Json,
) {
    val endpoint = SparqlEndpoint(store)

    routing {
        get(slug) {
            val query = call.parameters["query"] ?: run {
                call.respond(
                    status = HttpStatusCode.BadRequest,
                    message = "No query provided!"
                )
                return@get
            }
            val result = endpoint.onSelectQueryRequest(query = query)
            call.respond(result, serializer = json)
        }
        post(slug) {
            // if the content-type is ill-formed, this method can throw
            val type = runCatching { call.request.contentType() }.getOrNull()
            when {
                type == null -> {
                    call.respond(
                        status = HttpStatusCode.BadRequest,
                        message = "Invalid Content-Type headers"
                    )
                }
                type.match(SparqlContentType.SelectPostForm) -> {
                    val params = call.receiveParameters()
                    val query = params["query"] ?: run {
                        call.respond(
                            status = HttpStatusCode.BadRequest,
                            message = "No query provided!"
                        )
                        return@post
                    }
                    val result = endpoint.onSelectQueryRequest(query = query)
                    call.respond(result, serializer = json)
                }
                type.match(SparqlContentType.SelectPostBody) -> {
                    val result = endpoint.onSelectQueryRequest(query = call.receiveText())
                    call.respond(result, serializer = json)
                }
                type.match(SparqlContentType.UpdateQuery) -> {
                    val result = endpoint.onUpdateQueryRequest(query = call.receiveText())
                    call.respond(result) { cause -> "Invalid query! Caught the following exception.\n${cause.message}" }
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

/* helpers */

private suspend fun ApplicationCall.respond(
    result: Result<SelectResponse>,
    serializer: Json,
) {
    result.fold(
        onSuccess = { response ->
            respondBytesWriter(
                contentType = SparqlContentType.JsonBindings.withCharset(Charsets.UTF_8)
            ) {
                @OptIn(ExperimentalSerializationApi::class)
                serializer.encodeToStream(response, this.toOutputStream())
            }
        },
        onFailure = { cause ->
            respond(
                status = HttpStatusCode.BadRequest,
                message = "Invalid query! Caught the following exception.\n${cause.message}"
            )
        }
    )
}

private suspend fun ApplicationCall.respond(result: Result<Unit>, onFailure: (Throwable) -> String) {
    result.fold(
        onSuccess = { respond(HttpStatusCode.OK) },
        onFailure = { respond(status = HttpStatusCode.BadRequest, message = onFailure(it)) }
    )
}
