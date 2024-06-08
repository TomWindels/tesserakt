package sparql

import comunica.comunicaSelectQuery
import dev.tesserakt.rdf.types.Store
import dev.tesserakt.sparql.runtime.common.types.Bindings


actual suspend fun executeExternalQuery(query: String, data: Store): List<Bindings> {
    return data.comunicaSelectQuery(query)
}
