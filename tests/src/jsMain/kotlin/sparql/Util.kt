package sparql

import comunica.comunicaSelectQuery
import dev.tesserakt.interop.rdfjs.n3.N3Store
import dev.tesserakt.interop.rdfjs.toN3Store
import dev.tesserakt.rdf.types.Store
import dev.tesserakt.sparql.runtime.common.types.Bindings
import kotlin.time.measureTime


actual class ExternalQueryExecution actual constructor(
    private val query: String,
    data: Store
) {

    private val store: N3Store
    private val duration = measureTime {
        store = data.toN3Store()
    }

    actual suspend fun execute(): List<Bindings> {
        return store.comunicaSelectQuery(query)
    }

    actual fun report(): String {
        return " * Comunica query execution\n\tPreparation took $duration"
    }

}
