
import dev.tesserakt.interop.rdfjs.n3.N3Quad
import dev.tesserakt.interop.rdfjs.toN3Triple
import dev.tesserakt.interop.rdfjs.toQuad
import dev.tesserakt.rdf.serialization.DelicateSerializationApi
import dev.tesserakt.rdf.serialization.common.deserialize
import dev.tesserakt.rdf.turtle.serialization.TurtleSerializer
import dev.tesserakt.rdf.types.Store
import dev.tesserakt.rdf.types.consume
import dev.tesserakt.sparql.Compiler
import dev.tesserakt.sparql.Query
import dev.tesserakt.sparql.query
import dev.tesserakt.sparql.types.SelectQueryStructure
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlin.js.Promise

// IMPORTANT: this file cannot be part of a package, as otherwise `parse` & `query` are not properly accessible in the
//  exported module (they live in a matching namespace)

// see https://github.com/comunica/comunica/blob/v1.22.3/packages/actor-init-sparql/spec/sparql-engine-base.js
//  for an example of the available functions to export and their signatures ; adheres to the `QueryEngine` interface

// not adhering to the promise type as this causes odd printing behavior
@OptIn(ExperimentalJsExport::class, DelicateSerializationApi::class, DelicateCoroutinesApi::class)
@JsExport
fun parse(data: String, options: Any): Promise<dynamic> {
    if (options !is String) {
        throw IllegalArgumentException("Unknown options value: `${JSON.stringify(options)}`")
    }
    return Promise { resolve, reject ->
        val result = runCatching {
            when {
                "rdf-turtle" in options -> {
                    TurtleSerializer
                        .deserialize(data)
                        .consume()
                        .map { it.toN3Triple() }
                        .toTypedArray()
                }

                else -> {
                    // must be sparql?
                    Compiler().compile(data)
                }
            }
        }
        result.fold(
            onSuccess = { resolve(it) },
            onFailure = { reject(it) }
        )
    }
}

@OptIn(ExperimentalJsExport::class)
@JsExport
fun query(data: Array<N3Quad>, queryString: String, options: dynamic): QueryResult {
    val store = Store().also { it.addAll(data.map { n3Quad -> n3Quad.toQuad() }) }
    val compiled = Compiler().compile(queryString)
    return store
        .query(Query.Select(queryString))
        .let { QueryResultBindings(variables = (compiled.structure as SelectQueryStructure).bindings, bindings = it) }
}
