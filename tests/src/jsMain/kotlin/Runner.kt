import dev.tesserakt.sparql.Compiler
import kotlin.js.Promise

// IMPORTANT: this file cannot be part of a package, as otherwise `parse` & `query` are not properly accessible in the
//  exported module (they live in a matching namespace)

// see https://github.com/comunica/comunica/blob/v1.22.3/packages/actor-init-sparql/spec/sparql-engine-base.js
//  for an example of the available functions to export and their signatures

@OptIn(ExperimentalJsExport::class)
@JsExport
// incorrect warning
@Suppress("NON_EXPORTABLE_TYPE")
fun parse(queryString: String, options: dynamic): Promise<Unit> {
    console.log("== Incoming `parse` query:\n$queryString")
    runCatching { with (Compiler.Default) { queryString.asSPARQLQuery() } }
        .onSuccess { console.log("Compiled (I think)") }
        .onFailure {
            console.error("Did not compile!")
            console.error(it.stackTraceToString())
            // making sure the test suite is aware of our happy little accident
            throw it
        }
    return Promise.resolve(Unit)
}

@OptIn(ExperimentalJsExport::class)
@JsExport
fun query(data: Array<Quad>, queryString: String, options: dynamic): Promise<QueryResult<*>> {
    TODO()
}
