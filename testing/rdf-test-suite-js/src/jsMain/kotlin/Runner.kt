
import dev.tesserakt.interop.rdfjs.n3.N3Quad
import dev.tesserakt.interop.rdfjs.toQuad
import dev.tesserakt.rdf.types.Store
import dev.tesserakt.sparql.Compiler
import dev.tesserakt.sparql.Compiler.Default.asSPARQLSelectQuery
import dev.tesserakt.sparql.runtime.incremental.evaluation.query

// IMPORTANT: this file cannot be part of a package, as otherwise `parse` & `query` are not properly accessible in the
//  exported module (they live in a matching namespace)

// see https://github.com/comunica/comunica/blob/v1.22.3/packages/actor-init-sparql/spec/sparql-engine-base.js
//  for an example of the available functions to export and their signatures ; adheres to the `QueryEngine` interface

// not adhering to the promise type as this causes odd printing behavior
@OptIn(ExperimentalJsExport::class)
@JsExport
fun parse(queryString: String, options: Map<String, dynamic>) {
    with (Compiler.Default) { queryString.asSPARQLQuery() }
}

@OptIn(ExperimentalJsExport::class)
@JsExport
fun query(data: Array<N3Quad>, queryString: String, options: dynamic): QueryResult {
    val store = Store().also { it.addAll(data.map { n3Quad -> n3Quad.toQuad() }) }
    val query = queryString.asSPARQLSelectQuery()
    return store
        .query(query)
        .let { QueryResultBindings(variables = query.variables, bindings = it) }
}
