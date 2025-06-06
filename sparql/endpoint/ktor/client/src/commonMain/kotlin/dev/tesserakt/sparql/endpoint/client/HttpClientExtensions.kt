package dev.tesserakt.sparql.endpoint.client

import dev.tesserakt.sparql.Bindings
import dev.tesserakt.sparql.endpoint.core.SparqlContentType
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*


/**
 * Execute a SPARQL SELECT query according to the [SPARQL spec](https://www.w3.org/TR/sparql11-protocol/#query-operation).
 * The [path] represents the URL of the SPARQL endpoint.
 * The [mode] represents the approach that should be used to create the request. See [QueryOperationMode] for more
 *  information.
 */
suspend fun HttpClient.sparqlQuery(
    query: String,
    path: String = "sparql",
    mode: QueryOperationMode = QueryOperationMode.POST_BODY
): HttpResponse {
    return mode.exec(this, query, path)
}

/**
 * Read this [HttpResponse]'s body as a list of [Bindings].
 *
 * IMPORTANT: this requires [sparql] to be set-up using [ContentNegotiation](https://ktor.io/docs/client-serialization.html).
 */
suspend fun HttpResponse.bodyAsBindings(): List<Bindings> {
    return body<List<Bindings>>()
}

/**
 * Execute a SPARQL UPDATE query according to the [SPARQL spec](https://www.w3.org/TR/sparql11-protocol/#update-operation).
 * The [path] represents the URL of the SPARQL endpoint.
 * The [block] can be used to add data to be inserted and/or removed. See [SparqlUpdateRequestBuilder] for more
 *  information.
 */
suspend inline fun HttpClient.sparqlUpdate(
    path: String = "sparql",
    block: SparqlUpdateRequestBuilder.() -> Unit
): HttpResponse {
    val content = SparqlUpdateRequestBuilder().apply(block).optimise()
    return post(path) {
        contentType(SparqlContentType.UpdateQuery)
        setBody(content.toQueryString())
    }
}
