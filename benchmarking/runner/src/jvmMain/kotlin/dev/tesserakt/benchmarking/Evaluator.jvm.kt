package dev.tesserakt.benchmarking

import dev.tesserakt.interop.jena.toJenaDataset
import dev.tesserakt.rdf.types.MutableStore
import dev.tesserakt.sparql.benchmark.replay.SnapshotStore
import org.apache.jena.query.Dataset
import org.apache.jena.query.QueryExecutionFactory

actual class Reference actual constructor(private val query: String) : Evaluator() {

    private val total = MutableStore()
    private lateinit var store: Dataset

    override fun prepare(diff: SnapshotStore.Diff) {
        diff.insertions.forEach { total.add(it) }
        diff.deletions.forEach { total.remove(it) }
        store = total.toJenaDataset()
    }

    override fun apply() {
        QueryExecutionFactory.create(query, store).use { execution ->
            val solutions = execution.execSelect()
            while (solutions.hasNext()) {
                // consuming the solution
                solutions.next()
            }
        }
    }

}
