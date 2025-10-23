package dev.tesserakt.benchmarking.endpoint

import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.rdf.types.SnapshotStore
import dev.tesserakt.rdf.types.factory.MutableStore
import dev.tesserakt.rdf.types.toStore
import dev.tesserakt.sparql.Bindings
import dev.tesserakt.sparql.endpoint.client.*
import dev.tesserakt.util.replace
import io.ktor.client.*
import io.ktor.client.engine.java.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*

class EndpointEvaluator(
    private val queryUrl: String,
    private val updateUrl: String?,
    private val token: String?,
    private val query: String,
) {

    /**
     * An evaluation result, representing the number of [added] and [removed] bindings compared to the previous state,
     *  as well as a [checksum] value representing the contents
     */
    data class Output(
        val added: Int,
        val removed: Int,
        val checksum: Int,
    )

    private var previous = emptyList<Bindings>()
    private var current = emptyList<Bindings>()
    private var checksum = 0
    // keeping a local mirror of the endpoint state, so we remove it upon finishing
    private val mirror = MutableStore()

    private val client = HttpClient(Java) {
        install(ContentNegotiation) {
            sparql()
        }
    }

    /**
     * Prepares a diff to be evaluated, w/o actually evaluating it
     */
    suspend fun prepare(diff: SnapshotStore.Diff) {
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

    /**
     * Evaluates the diff; this is the method which execution time matters!
     */
    suspend fun eval() {
        current = client.sparqlQuery(endpoint = queryUrl, query = query)
            .also { checkSuccess(response = it) }
            .bodyAsBindings()
        checksum = current.sumOf { it.sumOf { it.second.checksumLength } }
    }

    /**
     * Evaluates the result difference after applying the diff
     */
    fun finish(): Output {
        val result = compare(current, previous, checksum = checksum)
        previous = current
        return result
    }

    suspend fun close() {
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

    suspend inline fun use(block: (EndpointEvaluator) -> Unit) {
        try {
            block(this)
        } finally {
            close()
        }
    }

    private suspend fun checkSuccess(response: HttpResponse) {
        check(response.status.isSuccess()) { "The endpoint reported an error: ${response.status}\n${response.bodyAsText()}" }
    }

    companion object {

        var REQUIRE_EMPTY_INITIAL_STATE: Boolean = true

        /**
         * Compares results from the first set [a] with the second set [b], where [a] represents the most up-to-date version
         *  of the result that is being compared
         */
        fun compare(a: List<Any>, b: List<Any>, checksum: Int): Output {
            val counts = a.groupingBy { it }.eachCount().toMutableMap()
            b.forEach {
                counts.replace(it) { v -> (v ?: 0) - 1 }
            }
            return Output(
                added = counts.filter { it.value > 0 }.entries.sumOf { entry -> entry.value },
                removed = counts.filter { it.value < 0 }.entries.sumOf { entry -> -entry.value },
                checksum = checksum,
            )
        }

    }

}

private val Quad.Element.checksumLength: Int
    get() = when (this) {
        is Quad.BlankTerm -> 1
        is Quad.Literal -> value.length
        is Quad.LangString -> value.length
        is Quad.NamedTerm -> value.length
        Quad.DefaultGraph -> 0
    }
