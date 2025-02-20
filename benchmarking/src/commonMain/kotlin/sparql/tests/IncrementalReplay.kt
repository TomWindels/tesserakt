package sparql.tests

import dev.tesserakt.rdf.serialization.common.Path
import dev.tesserakt.rdf.trig.serialization.TriGSerializer
import dev.tesserakt.rdf.types.MutableStore
import dev.tesserakt.rdf.types.consume
import dev.tesserakt.sparql.Compiler.Default.asSPARQLSelectQuery
import dev.tesserakt.sparql.benchmark.replay.ReplayBenchmark
import dev.tesserakt.sparql.benchmark.replay.SnapshotStore
import dev.tesserakt.sparql.runtime.common.types.Bindings
import dev.tesserakt.sparql.runtime.common.util.Debug
import dev.tesserakt.sparql.runtime.incremental.evaluation.query
import dev.tesserakt.util.printerrln
import sparql.ExternalQueryExecution
import sparql.types.OutputComparisonTest
import kotlin.time.measureTime

private data class ReplayTestResult(
    val result: OutputComparisonTest.Result,
    val storeSize: Int,
    val diff: SnapshotStore.Diff,
) {

    fun isSuccess() = result.isSuccess()

    override fun toString(): String = if (!isSuccess()) result.toString() else buildString {
        appendLine("Δ +${diff.insertions.size} quad(s), -${diff.deletions.size} quad(s)")
        appendLine("∑ $storeSize quad(s)")
        val ratio = (result.referenceTime.inWholeNanoseconds.toDouble() / result.elapsedTime.inWholeNanoseconds).toString()
        if (ratio.all { it.isDigit() || it == '.' }) {
            // if normal representation, truncating the format
            val shortened = if ('.' in ratio) {
                ratio.substring(0, endIndex = ratio.indexOfFirst { it == '.' } + 2)
            } else ratio
            append("P ${result.received.size} binding(s) in ${result.elapsedTime} (<-> ${result.referenceTime}, ${shortened}x)")
        } else {
            // else, when e.g. `1e-1`, keeping it
            append("P ${result.received.size} binding(s) in ${result.elapsedTime} (<-> ${result.referenceTime})")
        }
    }

}

suspend fun compareIncrementalStoreReplay(benchmarkFilepath: String) {
    val benchmark = ReplayBenchmark
        .from(store = TriGSerializer.deserialize(Path(benchmarkFilepath)).consume())
        .single()
    if (benchmark.queries.size == 1) {
        println("Found ${benchmark.queries.size} query that will be used on a store with ${benchmark.store.snapshotCount} snapshot(s)!")
    } else {
        println("Found ${benchmark.queries.size} queries that will be used on a store with ${benchmark.store.snapshotCount} snapshot(s)!")
    }
    benchmark.queries.forEachIndexed { i, query ->
        val store = MutableStore()
        val evaluation = store.query(query.asSPARQLSelectQuery())
        println("Beginning new evaluation for query ${i + 1}")
        var snapshotIndex = 0
        benchmark.eval { current, diff ->
            ++snapshotIndex
            println("# $snapshotIndex")
            // applying the diff to the active store, measuring the time it took for the evaluation to update
            val received: List<Bindings>
            val time = measureTime {
                store.apply(diff)
                received = evaluation.results
            }
            check(store.size == current.size)
            // comparing the results with the reference implementation, using the `current` store version's data
            val external: ExternalQueryExecution
            val solution: List<Bindings>
            val reference = measureTime {
                external = ExternalQueryExecution(query, current)
                solution = external.execute()
            }
            val comparison = OutputComparisonTest.Result.from(
                received = received,
                expected = solution,
                debugInformation = "${Debug.report()}${external.report()}",
                elapsedTime = time,
                referenceTime = reference
            )
            val result = ReplayTestResult(comparison, store.size, diff)
            if (!result.isSuccess()) {
                printerrln(result.toString())
                // bailing early
                return
            }
            println(result)
        }
    }
}

private fun MutableStore.apply(diff: SnapshotStore.Diff) {
    diff.insertions.forEach { add(it) }
    diff.deletions.forEach { remove(it) }
}
