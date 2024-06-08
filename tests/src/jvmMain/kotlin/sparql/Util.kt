package sparql

import dev.tesserakt.interop.jena.toJenaDataset
import dev.tesserakt.interop.jena.toTerm
import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.rdf.types.Store
import dev.tesserakt.sparql.runtime.common.types.Bindings
import org.apache.jena.query.QueryExecutionFactory


actual suspend fun executeExternalQuery(query: String, data: Store): List<Bindings> {
    val store = data.toJenaDataset()
    val results = mutableListOf<Bindings>()
    QueryExecutionFactory.create(query, store).use { execution ->
        val solutions = execution.execSelect()
        while (solutions.hasNext()) {
            val current = mutableMapOf<String, Quad.Term>()
            val solution = solutions.nextSolution()
            solution.varNames().forEach {
                current[it] = solution[it].asNode().toTerm()
            }
            results.add(current)
        }
    }
    return results
}
