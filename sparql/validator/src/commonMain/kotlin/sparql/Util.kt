package sparql

import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.sparql.Bindings


expect class ExternalQueryExecution(query: String, data: Collection<Quad>) {

    suspend fun execute(): List<Bindings>

    fun report(): String

}

internal expect fun readFile(filepath: String): Result<String>
