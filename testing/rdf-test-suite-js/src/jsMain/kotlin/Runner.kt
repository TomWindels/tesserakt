
import dev.tesserakt.interop.rdfjs.n3.N3Quad
import dev.tesserakt.interop.rdfjs.toQuad
import dev.tesserakt.rdf.types.ReadOnlyStore
import dev.tesserakt.sparql.Compiler
import dev.tesserakt.sparql.Query
import dev.tesserakt.sparql.query
import dev.tesserakt.sparql.types.SelectQueryStructure

// IMPORTANT: this file cannot be part of a package, as otherwise `parse` & `query` are not properly accessible in the
//  exported module (they live in a matching namespace)

// see https://github.com/comunica/comunica/blob/v1.22.3/packages/actor-init-sparql/spec/sparql-engine-base.js
//  for an example of the available functions to export and their signatures ; adheres to the `QueryEngine` interface

// not adhering to the promise type as this causes odd printing behavior
@OptIn(ExperimentalJsExport::class)
@JsExport
fun parse(queryString: String, options: Map<String, dynamic>) {
    Compiler().compile(queryString)
}

@OptIn(ExperimentalJsExport::class)
@JsExport
fun query(data: Array<N3Quad>, queryString: String, options: dynamic): QueryResult {
    val store = ReadOnlyStore(data.map { it.toQuad() })
    val compiled = Compiler().compile(queryString)
    return store
        .query(Query.Select(queryString))
        .let { QueryResultBindings(variables = (compiled.structure as SelectQueryStructure).bindings, bindings = it) }
}
