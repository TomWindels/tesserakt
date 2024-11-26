package sparql

import dev.tesserakt.interop.jena.toJenaDataset
import dev.tesserakt.interop.jena.toTerm
import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.sparql.runtime.common.types.Bindings
import org.apache.jena.query.Dataset
import org.apache.jena.query.QueryExecutionFactory
import kotlin.io.path.Path
import kotlin.io.path.readText
import kotlin.time.measureTime

actual class ExternalQueryExecution actual constructor(
    private val query: String,
    data: Collection<Quad>
) {

    private val store: Dataset
    private val duration = measureTime {
        store = data.toJenaDataset()
    }

    actual suspend fun execute(): List<Bindings> {
        val results = mutableListOf<Bindings>()
        QueryExecutionFactory.create(query, store).use { execution ->
            val solutions = execution.execSelect()
            while (solutions.hasNext()) {
                val current = mutableMapOf<String, Quad.Term>()
                val solution = solutions.nextSolution()
                solution.varNames().forEach { name ->
                    solution[name]?.asNode()?.toTerm()?.let { current[name] = it }
                }
                results.add(current)
            }
        }
        return results
    }

    actual fun report(): String {
        return " * Jena query execution\n\tPreparation took $duration"
    }

}

internal actual fun readFile(filepath: String): Result<String> = runCatching {
    Path(filepath).readText()
}
