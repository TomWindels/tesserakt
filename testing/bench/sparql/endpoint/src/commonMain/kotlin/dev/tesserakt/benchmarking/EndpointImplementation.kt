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
import io.ktor.client.statement.*
import io.ktor.http.*

class EndpointImplementation(
    private val endpoint: String,
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
        if (mirror.isEmpty()) {
            // TODO when supported in tesserakt, a simple `COUNT` or `ASK` should suffice & limit overhead
            val bindings = client
                .sparqlQuery(endpoint, "SELECT * WHERE { ?s ?p ?o }")
                .also { checkSuccess(response = it) }
                .bodyAsBindings()
            check(bindings.isEmpty()) { "Expected an empty state, but received ${bindings.size} binding(s) instead:\n${"\n"}" }
        }
        mirror.apply {
            addAll(diff.insertions)
            removeAll(diff.deletions)
        }
        client.sparqlUpdate(endpoint = endpoint) {
            add(diff.insertions.toStore())
            remove(diff.deletions.toStore())
        }.also { checkSuccess(response = it) }
    }

    override suspend fun eval() {
        current = client.sparqlQuery(endpoint = endpoint, query = query)
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
        client.sparqlUpdate(endpoint = endpoint) {
            remove(mirror)
        }
    }

    private suspend fun checkSuccess(response: HttpResponse) {
        check(response.status.isSuccess()) { "The endpoint reported an error: ${response.status}\n${response.bodyAsText()}" }
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
