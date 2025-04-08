package sparql.tests

import dev.tesserakt.rdf.serialization.common.Path
import dev.tesserakt.rdf.trig.serialization.TriGSerializer
import dev.tesserakt.rdf.types.MutableStore
import dev.tesserakt.rdf.types.SnapshotStore
import dev.tesserakt.rdf.types.consume
import dev.tesserakt.sparql.Bindings
import dev.tesserakt.sparql.Query
import dev.tesserakt.sparql.benchmark.replay.ReplayBenchmark
import dev.tesserakt.sparql.query
import dev.tesserakt.sparql.runtime.RuntimeStatistics
import dev.tesserakt.util.printerrln
import sparql.ExternalQueryExecution
import sparql.types.OutputComparisonTest
import sparql.types.fastCompare
import kotlin.time.measureTime

private data class ReplayTestResult(
    val previous: List<Bindings>?,
    val result: OutputComparisonTest.Result,
    val storeSize: Int,
    val diff: SnapshotStore.Diff,
) {

    private val comparison = previous?.let { fastCompare(it, result.received) }

    fun isSuccess() = result.isSuccess()

    override fun toString(): String = if (!isSuccess()) result.toString() else buildString {
        appendLine("× Input")
        appendLine(inputReport().prependIndent("  "))
        appendLine("× Output")
        appendLine(outputReport().prependIndent("  "))
        appendLine("× Time statistics")
        append(timeReport().prependIndent("  "))
    }

    private fun inputReport(): String = buildString {
        appendLine("∑ $storeSize quad(s) total")
        append("Δ ${diff.insertions.size} added, ${diff.deletions.size} removed")
    }

    private fun outputReport(): String = if (comparison == null) "${result.received.size} binding(s)" else buildString {
        appendLine("∑ ${result.received.size} binding(s) total")
        append("Δ ${comparison.leftOver.size} added, ${comparison.missing.size} removed")
    }

    private fun timeReport(): String = buildString {
        append("Incremental: ")
        appendLine(result.elapsedTime)
        append("Reference: ")
        val ratio = (result.referenceTime.inWholeNanoseconds.toDouble() / result.elapsedTime.inWholeNanoseconds).toString()
        append(result.referenceTime)
        if (ratio.all { it.isDigit() || it == '.' }) {
            // if normal representation, truncating the format
            val shortened = if ('.' in ratio) {
                ratio.substring(0, endIndex = ratio.indexOfFirst { it == '.' } + 2)
            } else ratio
            append(", ${shortened}x")
        }
    }

}

expect fun awaitBenchmarkStart()

suspend fun compareIncrementalStoreReplay(benchmarkFilepath: String) {
    val benchmark = ReplayBenchmark
        .from(store = TriGSerializer.deserialize(Path(benchmarkFilepath)).consume())
        .single()
    if (benchmark.queries.size == 1) {
        println("Found ${benchmark.queries.size} query that will be used on a store with ${benchmark.store.snapshotCount} snapshot(s)!")
    } else {
        println("Found ${benchmark.queries.size} queries that will be used on a store with ${benchmark.store.snapshotCount} snapshot(s)!")
    }
    awaitBenchmarkStart()
    benchmark.queries.forEachIndexed { i, query ->
        val store = MutableStore()
        val evaluation = store.query(Query.Select(query))
        println("Beginning new evaluation for query ${i + 1}")
        var snapshotIndex = 0
        var previous: List<Bindings>? = null
        benchmark.eval { current, diff ->
            ++snapshotIndex
            println("# $snapshotIndex")
            // applying the diff to the active store, measuring the time it took for the evaluation to update
            val received: List<Bindings>
            val time = measureTime {
                store.apply(diff)
                received = evaluation.results.toList()
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
                debugInformation = "${RuntimeStatistics.report()}${external.report()}",
                elapsedTime = time,
                referenceTime = reference
            )
            val result = ReplayTestResult(previous, comparison, store.size, diff)
            previous = result.result.received
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
