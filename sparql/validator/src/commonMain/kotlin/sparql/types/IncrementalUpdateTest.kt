package sparql.types

import dev.tesserakt.rdf.types.ObservableStore
import dev.tesserakt.rdf.types.Store
import dev.tesserakt.sparql.Bindings
import dev.tesserakt.sparql.QueryStatistics
import dev.tesserakt.sparql.evaluation.OngoingQueryEvaluation
import dev.tesserakt.sparql.query
import dev.tesserakt.testing.Test
import dev.tesserakt.testing.runTest
import sparql.ExternalQueryExecution
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.measureTime

class IncrementalUpdateTest(
    query: String,
    store: Store
) : QueryExecutionTest(query, store) {

    override suspend fun test() = runTest {
        val input = ObservableStore()
        val builder = Result.Builder(store)
        suspend fun reference(): Pair<Duration, List<Bindings>> {
            val external = ExternalQueryExecution(queryString, input)
            val results: List<Bindings>
            val elapsed = measureTime {
                try {
                    results = external.execute()
                } catch (t: Throwable) {
                    throw RuntimeException("Failed to use external implementation reference: ${t.message}", t)
                }
            }
            return elapsed to results
        }
        val ongoing: OngoingQueryEvaluation<Bindings>
        val setupTime= measureTime {
            ongoing = input.query(query)
        }
        // checking the initial state (no data)
        val success = builder.add(
            self = setupTime to ongoing.results.toList(),
            reference = reference(),
            strictOrdering = hasStrictOrdering,
            statistics = ongoing.stats()
        )
        check(success) { "Initial state output mismatch!" }
        // building it up
        store.forEachIndexed { i, quad ->
            val current: List<Bindings>
            val elapsedTime = measureTime {
                try {
                    input.add(quad)
                    current = ongoing.results.toList()
                } catch (t: Throwable) {
                    val result = builder.build()
                    throw RuntimeException(
                        "Query failure after change #${i + 1}\nPrevious results:\n${result.outputs.takeLast(3).joinToString("\n")}",
                        t
                    )
                }
            }
            val success = builder.add(
                self = elapsedTime to current,
                reference = reference(),
                strictOrdering = hasStrictOrdering,
                statistics = ongoing.stats()
            )
            check(success) {
                val result = builder.build()
                "Output mismatch after change #${i + 1}\n${result.outputs.takeLast(3).joinToString("\n")}\n${result.outputs.last().exceptionOrNull()?.message ?: "Detailed contents unavailable"}"
            }
        }
        // breaking it back down
        store.forEachIndexed { i, quad ->
            val current: List<Bindings>
            val elapsedTime = measureTime {
                try {
                    input.remove(quad)
                    current = ongoing.results.toList()
                } catch (t: Throwable) {
                    val result = builder.build()
                    throw RuntimeException(
                        "Query failure after change #${store.size + i + 1}\nPrevious results:\n${result.outputs.takeLast(3).joinToString("\n")}",
                        t
                    )
                }
            }
            val success = builder.add(
                self = elapsedTime to current,
                reference = reference(),
                strictOrdering = hasStrictOrdering,
                statistics = ongoing.stats()
            )
            check(success) {
                val result = builder.build()
                "Output mismatch after change #${store.size + i + 1}\n${result.outputs.takeLast(3).joinToString("\n")}\n${result.outputs.last().exceptionOrNull()?.message ?: "Detailed contents unavailable"}"
            }
        }
        builder.build()
    }

    override fun toString(): String =
        "Incremental update SPARQL output comparison test\n * Query: `${
            queryString.replace(Regex("\\s+"), " ").trim()
        }`\n * Input: store with ${store.size} quad(s)"

    data class Result(
        val store: Store,
        val outputs: List<OutputComparisonTest.Result>
    ): Test.Result {

        class Builder(private val store: Store) {

            private val list = ArrayList<OutputComparisonTest.Result>(store.size * 2)

            fun add(
                self: Pair<Duration, List<Bindings>>,
                reference: Pair<Duration, List<Bindings>>,
                strictOrdering: Boolean,
                statistics: QueryStatistics,
            ): Boolean {
                val result = compare(
                    received = self.second,
                    elapsedTime = self.first,
                    expected = reference.second,
                    referenceTime = reference.first,
                    strictOrdering = strictOrdering,
                    statistics = statistics
                )
                list.add(result)
                return result.isSuccess()
            }

            fun build() = Result(store = store, outputs = list)

        }

        override fun isSuccess(): Boolean = outputs.all { it.isSuccess() }

        override fun exceptionOrNull(): Throwable? {
            val index = outputs.indexOfFirst { !it.isSuccess() }
            if (index == -1) {
                return null
            }
            return AssertionError(
                buildString {
                    when {
                        index == 0 -> {
                            // initial state failed
                            appendLine("Comparison failed without any data")
                        }
                        index <= store.size -> {
                            // insertion failed
                            appendLine("First failure occurred at incremental change #$index")
                            appendLine("\t[+] ${store.elementAt(index - 1)}")
                        }
                        else -> {
                            // deletion failed
                            appendLine("First failure occurred at incremental change #$index")
                            appendLine("\t[-] ${store.elementAt(index - store.size - 1)}")
                        }
                    }
                    append(outputs[index].exceptionOrNull()?.message ?: "Detailed contents unavailable")
                }
            )
        }

        override fun toString(): String {
            val min = outputs.minOf { it.elapsedTime }
            val max = outputs.maxOf { it.elapsedTime }
            val mean = (outputs.sumOf { it.elapsedTime.inWholeNanoseconds } / outputs.size).nanoseconds
            return buildString {
                appendLine(" * ${outputs.count { it.isSuccess() } } / ${outputs.size} individual output(s) matched")
                if (outputs.size > 3) {
                    appendLine("\t...")
                    repeat(3) {
                        val i = outputs[outputs.size - 3 + it]
                        appendLine("\t${i.summary()}")
                    }
                }
                append(" * Incremental time characteristics\n\tmin: $min, mean: $mean, max: $max")
            }
        }

    }

}

// helpers
private fun OutputComparisonTest.Result.summary() =
    "${received.size} received, ${expected.size} expected, ${missing.size} missing, ${leftOver.size} superfluous"
