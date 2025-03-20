package dev.tesserakt.benchmarking

import dev.tesserakt.interop.jena.toJenaQuad
import dev.tesserakt.sparql.benchmark.replay.SnapshotStore
import org.apache.jena.query.DatasetFactory
import org.apache.jena.query.QueryExecutionFactory
import org.apache.jena.query.QuerySolution
import org.apache.jena.query.ReadWrite

actual class Reference actual constructor(private val query: String) : Evaluator() {

    private val store = DatasetFactory.createTxnMem()
    private var previous = emptyList<QuerySolution>()
    private var current = emptyList<QuerySolution>()

    override fun prepare(diff: SnapshotStore.Diff) {
        store.begin(ReadWrite.WRITE)
        try {
            val graph = store.asDatasetGraph()
            diff.insertions.forEach { graph.add(it.toJenaQuad()) }
            diff.deletions.forEach { graph.delete(it.toJenaQuad()) }
            store.commit()
        } finally {
            store.end()
        }
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
