package dev.tesserakt.sparql.endpoint.server

import dev.tesserakt.sparql.endpoint.core.SparqlContentType
import dev.tesserakt.sparql.endpoint.core.data.UpdateRequest
import dev.tesserakt.sparql.endpoint.server.impl.CachingSparqlEndpoint
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * Attaches a SPARQL endpoint at the given point in the [Route] with a given [path] as name, using the provided [endpoint] as
 *  the to-be-queried and updated state. Additional serialization customisation w.r.t. the JSON format can be done
 *  using the [formatter] parameter.
 */
fun Route.sparqlEndpoint(
    /** The path name used to make this endpoint available **/
    path: String = "sparql",
    /** The actual [SparqlEndpoint] instance, responsible for processing the requests **/
    endpoint: SparqlEndpoint = CachingSparqlEndpoint(),
    /** The used [ResultFormatter] instance to serialize binding results with **/
    formatter: ResultFormatter = ResultFormatter(),
) {
    get(path) {
        val query = call.parameters["query"] ?: run {
            call.respond(
                status = HttpStatusCode.BadRequest,
                message = "No query provided!"
            )
            return@get
        }
        val result = endpoint.onSelectQueryRequest(query = query)
        formatter.respond(call, result)
    }
    post(path) {
        // if the content-type is ill-formed, this method can throw
        val type = runCatching { call.request.contentType() }.getOrNull()
        val result = when {
            type == null -> {
                call.respond(
                    status = HttpStatusCode.BadRequest,
                    message = "Invalid Content-Type headers"
                )
                return@post
            }
            type.match(SparqlContentType.FormPost) -> {
                // joining the total list of params together, from form parameters & url parameters
                val params = call.receiveParameters()
                if ("query" in params) {
                    endpoint.onSelectQueryRequest(query = params["query"]!!)
                } else if ("update" in params) {
                    val result = endpoint.onUpdateQueryRequest(request = UpdateRequest.parse(params["update"]!!))
                    call.respond(result) { cause -> "Invalid query! Caught the following exception.\n${cause.message}" }
                    return@post
                } else {
                    call.respond(
                        status = HttpStatusCode.BadRequest,
                        message = "No query provided!"
                    )
                    return@post
                }
            }
            type.match(SparqlContentType.SelectPostBody) -> {
                endpoint.onSelectQueryRequest(query = call.receiveText())
            }
            type.match(SparqlContentType.UpdateQuery) -> {
                val result = endpoint.onUpdateQueryRequest(request = UpdateRequest.parse(call.receiveText()))
                call.respond(result) { cause -> "Invalid query! Caught the following exception.\n${cause.message}" }
                return@post
            }
            else -> {
                call.respond(
                    status = HttpStatusCode.BadRequest,
                    message = "Invalid Content-Type headers"
                )
                return@post
            }
        }
        formatter.respond(call, result)
    }
}

/* helpers */

private suspend fun ApplicationCall.respond(result: Result<Unit>, onFailure: (Throwable) -> String) {
    result.fold(
        onSuccess = { respond(HttpStatusCode.OK) },
        onFailure = { respond(status = HttpStatusCode.BadRequest, message = onFailure(it)) }
    )
}
