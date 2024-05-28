import dev.tesserakt.sparql.Compiler
import kotlin.js.Promise

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
fun query(data: Array<Quad>, queryString: String, options: dynamic): Promise<QueryResult<*>> {
    TODO()
}
