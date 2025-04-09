package dev.tesserakt.benchmarking

import dev.tesserakt.interop.jena.toJenaQuad
import dev.tesserakt.rdf.types.SnapshotStore
import org.apache.jena.graph.Node_Blank
import org.apache.jena.graph.Node_Literal
import org.apache.jena.graph.Node_URI
import org.apache.jena.query.DatasetFactory
import org.apache.jena.query.QueryExecutionFactory
import org.apache.jena.query.QuerySolution
import org.apache.jena.query.ReadWrite

class JenaReference(private val query: String) : Reference() {

    private val store = DatasetFactory.createTxnMem()
    private var previous = emptyList<QuerySolution>()
    private var current = emptyList<QuerySolution>()

    private var checksum = 0

    override fun prepare(diff: SnapshotStore.Diff) {
        store.begin(ReadWrite.WRITE)
        try {
            val graph = store.asDatasetGraph()
            diff.deletions.forEach { graph.delete(it.toJenaQuad()) }
            diff.insertions.forEach { graph.add(it.toJenaQuad()) }
            store.commit()
        } finally {
            store.end()
        }
    }

    override suspend fun eval() {
        QueryExecutionFactory.create(query, store).use { execution ->
            val results = mutableListOf<QuerySolution>()
            val solutions = execution.execSelect()
            // consuming the solution
            while (solutions.hasNext()) {
                val solution = solutions.next()
                results.add(solution)
                solution.varNames().forEach {
                    checksum += when (val variable = solution[it].asNode()) {
                        is Node_URI -> variable.uri.length
                        is Node_Literal -> variable.literalValue.toString().length
                        is Node_Blank -> variable.blankNodeLabel.length
                        else -> throw IllegalArgumentException("Unknown node type `${this::class.simpleName}`")
                    }
                }
            }
            current = results
        }
    }

    override fun finish(): Output {
        val result = compare(current, previous, checksum = checksum)
        checksum = 0
        previous = current
        return result
    }

}
