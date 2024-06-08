package sparql

import dev.tesserakt.rdf.types.Store
import dev.tesserakt.sparql.runtime.common.types.Bindings


expect suspend fun executeExternalQuery(query: String, data: Store): List<Bindings>
