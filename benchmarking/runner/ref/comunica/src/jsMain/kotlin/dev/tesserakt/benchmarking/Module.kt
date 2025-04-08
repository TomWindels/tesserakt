@file:JsModule("@comunica/query-sparql")
@file:JsNonModule
package dev.tesserakt.benchmarking

import dev.tesserakt.interop.rdfjs.n3.N3Term
import kotlin.js.Promise

@JsName("QueryEngine")
external class ComunicaQueryEngine {

    @JsName("queryBindings")
    fun query(query: String, options: dynamic) : Promise<ComunicaBindingStream>

}

external class ComunicaBindingStream {

    fun toArray(): Promise<Array<ComunicaBinding>>

}

external class ComunicaBinding {

    fun get(name: String): N3Term?

    fun forEach(fn: (value: N3Term, key: /* RDF.Variable */ dynamic) -> Unit)

    @JsName("toString")
    fun toPrettyString(): String

}
