package dev.tesserakt.sparql.endpoint.client

import dev.tesserakt.sparql.endpoint.core.SparqlContentType
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*


enum class QueryOperationMode(
    internal val exec: suspend (client: HttpClient, query: String, path: String) -> HttpResponse
) {
    GET(exec = { client, query, path -> client.get(path) { parameter("query", query) } }),
    POST_FORM(exec = { client, query, path ->
        client.submitForm(
            path,
            formParameters = parametersOf("query" to listOf(query))
        ) {
            contentType(SparqlContentType.SelectPostForm)
        }
    }),
    POST_BODY(exec = { client, query, path ->
        client.post(path) {
            contentType(SparqlContentType.SelectPostBody)
            setBody(query)
        }
    }),
}
