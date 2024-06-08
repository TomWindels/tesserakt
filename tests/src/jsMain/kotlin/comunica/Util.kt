package comunica

import dev.tesserakt.interop.rdfjs.n3.N3Term
import dev.tesserakt.interop.rdfjs.toN3Store
import dev.tesserakt.interop.rdfjs.toTerm
import dev.tesserakt.rdf.types.Store
import dev.tesserakt.sparql.runtime.common.types.Bindings
import kotlinx.coroutines.await

private val engine = ComunicaQueryEngine()

private fun ComunicaBinding.toBindings(): Bindings = js("Array")
    .from(this)
    .unsafeCast<Array<Array<dynamic>>>()
    // [ [ name, term | undefined ], ... ]
    .associate { (name, term) -> name.value as String to term.unsafeCast<N3Term>().toTerm() }

suspend fun Store.comunicaSelectQuery(query: String): List<Bindings> {
    val n3store = toN3Store()
    val opts: dynamic = Any()
    opts.sources = arrayOf(n3store)
    return engine
        .query(query, opts)
        .await()
        .toArray()
        .await()
        .map { binding: ComunicaBinding -> binding.toBindings() }
}
