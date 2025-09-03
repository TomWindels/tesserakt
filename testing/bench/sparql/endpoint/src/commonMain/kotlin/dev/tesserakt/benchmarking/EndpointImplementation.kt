package dev.tesserakt.benchmarking

import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.rdf.types.SnapshotStore
import dev.tesserakt.rdf.types.factory.MutableStore
import dev.tesserakt.rdf.types.toStore
import dev.tesserakt.sparql.Bindings
import dev.tesserakt.sparql.endpoint.client.*
import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*

class EndpointImplementation(
    private val queryUrl: String,
    private val updateUrl: String?,
    private val token: String?,
    private val query: String,
): Reference() {

    private var previous = emptyList<Bindings>()
    private var current = emptyList<Bindings>()
    private var checksum = 0
    // keeping a local mirror of the endpoint state, so we remove it upon finishing
    private val mirror = MutableStore()

    private val client = HttpClient(engine) {
        install(ContentNegotiation) {
            sparql()
        }
    }

    override suspend fun prepare(diff: SnapshotStore.Diff) {
        // ensuring that, if we don't expect any data, the endpoint is also empty
        if (REQUIRE_EMPTY_INITIAL_STATE && mirror.isEmpty()) {
            // TODO when supported in tesserakt, a simple `COUNT` or `ASK` should suffice & limit overhead
            val bindings = client
                .sparqlQuery(queryUrl, "SELECT * WHERE { ?s ?p ?o }")
                .also { checkSuccess(response = it) }
                .bodyAsBindings()
            check(bindings.isEmpty()) { "Expected an empty state, but received ${bindings.size} binding(s) instead:\n${"\n"}" }
        }
        mirror.apply {
            addAll(diff.insertions)
            removeAll(diff.deletions)
        }
        // sending updates in chunks of 1k triples
        diff.insertions.chunked(1000).forEach { insertionChunk ->
            client.submitForm(
                url = updateUrl ?: throw UnsupportedOperationException("No SPARQL Update URL provided!"),
                formParameters = ParametersBuilder()
                    .apply {
                        append(
                            name = "update",
                            value = SparqlUpdateRequestBuilder().apply {
                                add(insertionChunk.toStore())
                            }.toQueryString()
                        )
                        if (token != null) {
                            append("access-token", token)
                        }
                    }.build()
            ).also { checkSuccess(response = it) }
        }
        diff.deletions.chunked(1000).forEach { deletionChunk ->
            client.submitForm(
                url = updateUrl ?: throw UnsupportedOperationException("No SPARQL Update URL provided!"),
                formParameters = ParametersBuilder()
                    .apply {
                        append(
                            name = "update",
                            value = SparqlUpdateRequestBuilder().apply {
                                remove(deletionChunk.toStore())
                            }.toQueryString()
                        )
                        if (token != null) {
                            append("access-token", token)
                        }
                    }.build()
            ).also { checkSuccess(response = it) }
        }
    }

    override suspend fun eval() {
        current = client.sparqlQuery(endpoint = queryUrl, query = query)
            .also { checkSuccess(response = it) }
            .bodyAsBindings()
        checksum = current.sumOf { it.sumOf { it.second.checksumLength } }
    }

    override fun finish(): Output {
        val result = compare(current, previous, checksum = checksum)
        previous = current
        return result
    }

    override suspend fun close() {
        if (mirror.isNotEmpty()) {
            // sending updates in chunks of 1k triples
            mirror.chunked(1000).forEach { deletionChunk ->
                client.submitForm(
                    url = updateUrl ?: throw UnsupportedOperationException("No SPARQL Update URL provided!"),
                    formParameters = ParametersBuilder()
                        .apply {
                            append(
                                name = "update",
                                value = SparqlUpdateRequestBuilder().apply {
                                    remove(deletionChunk.toStore())
                                }.toQueryString()
                            )
                            if (token != null) {
                                append("access-token", token)
                            }
                        }.build()
                ).also { checkSuccess(response = it) }
            }
        }
    }

    private suspend fun checkSuccess(response: HttpResponse) {
        check(response.status.isSuccess()) { "The endpoint reported an error: ${response.status}\n${response.bodyAsText()}" }
    }

    companion object {

        var REQUIRE_EMPTY_INITIAL_STATE: Boolean = true

    }

}

internal expect val engine: HttpClientEngineFactory<*>

private val Quad.Element.checksumLength: Int
    get() = when (this) {
        is Quad.BlankTerm -> 1
        is Quad.Literal -> value.length
        is Quad.LangString -> value.length
        is Quad.NamedTerm -> value.length
        Quad.DefaultGraph -> 0
    }
