package dev.tesserakt.sparql.endpoint.client

import dev.tesserakt.sparql.endpoint.core.SparqlContentType
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*


/**
 * Models the different possible approaches to issue a SPARQL SELECT request, as described in the
 *  [SPARQL spec](https://www.w3.org/TR/sparql11-protocol/#query-operation).
 */
enum class QueryOperationMode(
    internal val exec: suspend (client: HttpClient, query: String, path: String, block: HttpRequestBuilder.() -> Unit) -> HttpResponse
) {
    /**
     * Execute the SPARQL SELECT request using a `GET` request and URL parameters.
     */
    GET(exec = { client, query, path, block -> client.get(path) { parameter("query", query); block() } }),

    /**
     * Execute the SPARQL SELECT request using a `POST` request and from parameters, with the
     *  `application/x-www-form-urlencoded` Content Type.
     */
    POST_FORM(exec = { client, query, path, block ->
        client.submitForm(
            path,
            formParameters = parametersOf("query" to listOf(query))
        ) {
            contentType(SparqlContentType.FormPost)
            block()
        }
    }),

    /**
     * Execute the SPARQL SELECT request using a `POST` request and a request body, with the
     *  `application/sparql-query` Content Type.
     */
    POST_BODY(exec = { client, query, path, block ->
        client.post(path) {
            contentType(SparqlContentType.SelectPostBody)
            setBody(query)
            block()
        }
    }),
}
