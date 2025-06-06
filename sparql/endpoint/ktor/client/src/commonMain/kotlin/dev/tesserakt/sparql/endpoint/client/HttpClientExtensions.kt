package dev.tesserakt.sparql.endpoint.client

import dev.tesserakt.sparql.Bindings
import dev.tesserakt.sparql.endpoint.core.SparqlContentType
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*


suspend fun HttpClient.sparqlQuery(
    query: String,
    path: String = "sparql",
    mode: QueryOperationMode = QueryOperationMode.POST_BODY
): HttpResponse {
    return mode.exec(this, query, path)
}

suspend fun HttpResponse.bodyAsBindings(): List<Bindings> {
    return body<List<Bindings>>()
}

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
