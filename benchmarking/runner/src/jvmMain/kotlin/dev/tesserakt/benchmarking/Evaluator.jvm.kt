package dev.tesserakt.benchmarking

import dev.tesserakt.interop.jena.toJenaDataset
import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.sparql.benchmark.replay.SnapshotStore
import org.apache.jena.query.Dataset
import org.apache.jena.query.QueryExecutionFactory
import org.apache.jena.query.QuerySolution

actual class Reference actual constructor(private val query: String) : Evaluator() {

    private val total = mutableSetOf<Quad>()
    private lateinit var store: Dataset
    private var previous = emptyList<QuerySolution>()
    private var current = emptyList<QuerySolution>()

    override fun prepare(diff: SnapshotStore.Diff) {
        diff.deletions.forEach { total.remove(it) }
        diff.insertions.forEach { total.add(it) }
        store = total.toJenaDataset()
    }

    override fun eval() {
        QueryExecutionFactory.create(query, store).use { execution ->
            val results = mutableListOf<QuerySolution>()
            val solutions = execution.execSelect()
            // consuming the solution
            while (solutions.hasNext()) {
                results.add(solutions.next())
            }
            current = results
        }
    }

    override fun finish(): Output {
        val result = compare(current, previous)
        previous = current
        return result
    }

}
