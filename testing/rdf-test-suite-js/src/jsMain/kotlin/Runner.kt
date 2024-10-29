
import dev.tesserakt.interop.rdfjs.n3.N3Quad
import dev.tesserakt.interop.rdfjs.toQuad
import dev.tesserakt.rdf.types.Store
import dev.tesserakt.sparql.Compiler
import dev.tesserakt.sparql.Compiler.Default.asSPARQLQuery
import dev.tesserakt.sparql.runtime.incremental.query.IncrementalQuery.Companion.query
import dev.tesserakt.sparql.runtime.incremental.query.IncrementalSelectQuery

// IMPORTANT: this file cannot be part of a package, as otherwise `parse` & `query` are not properly accessible in the
//  exported module (they live in a matching namespace)

// see https://github.com/comunica/comunica/blob/v1.22.3/packages/actor-init-sparql/spec/sparql-engine-base.js
//  for an example of the available functions to export and their signatures ; adheres to the `QueryEngine` interface

// not adhering to the promise type as this causes odd printing behavior
@OptIn(ExperimentalJsExport::class)
@JsExport
fun parse(queryString: String, options: Map<String, dynamic>) {
    runCatching { with (Compiler.Default) { queryString.asSPARQLQuery() } }
        .onSuccess {
            val msg = buildString {
                appendLine("== Incoming `parse` query")
                appendLine(queryString)
                appendLine("Compiled!")
            }
            console.log(msg)
        }
        .onFailure {
            val msg = buildString {
                appendLine("== Incoming `parse` query")
                appendLine(queryString)
                appendLine("Did not compile!")
                append(it.stackTraceToString())
            }
            console.error(msg)
            // making sure the test suite is aware of our happy little accident
            throw it
        }
}

@OptIn(ExperimentalJsExport::class)
@JsExport
fun query(data: Array<N3Quad>, queryString: String, options: dynamic): QueryResult {
    console.log("Executing the following query on ${data.size} triple(s): ```\n$queryString```")
    val store = Store().also { it.addAll(data.map { n3Quad -> n3Quad.toQuad() }) }
        .also { console.log(it.toString()) }
    val query = try {
        queryString.asSPARQLQuery()
    } catch (t: Throwable) {
        val msg = buildString {
            appendLine("== Couldn't validate query output as the query did not compile!")
            appendLine(queryString)
            appendLine("Did not compile!")
            append(t.stackTraceToString())
        }
        console.error(msg)
        // making sure the test suite is aware of our happy little accident
        throw t
    }
    return when (query) {
        is IncrementalSelectQuery -> {
            store
                .query(query)
                .also { console.log("It yielded ${it.size} binding(s)!") }
                .let { createSelectResult(bindings = it) }
        }
    }
}
