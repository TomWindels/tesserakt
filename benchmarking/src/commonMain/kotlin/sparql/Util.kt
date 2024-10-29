package sparql

import dev.tesserakt.rdf.types.Store
import dev.tesserakt.sparql.runtime.common.types.Bindings


expect class ExternalQueryExecution(query: String, data: Store) {

    suspend fun execute(): List<Bindings>

    fun report(): String

}

internal expect fun readFile(filepath: String): Result<String>
