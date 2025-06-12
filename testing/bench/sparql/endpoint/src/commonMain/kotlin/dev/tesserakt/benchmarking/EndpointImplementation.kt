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
        mirror.apply {
            addAll(diff.insertions)
            removeAll(diff.deletions)
        }
        client.sparqlUpdate(endpoint = endpoint) {
            add(diff.insertions.toStore())
            remove(diff.deletions.toStore())
        }
    }

    override suspend fun eval() {
        current = client.sparqlQuery(endpoint = endpoint, query = query).bodyAsBindings()
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
