package sparql.types

import dev.tesserakt.rdf.types.ObservableStore
import dev.tesserakt.rdf.types.Store
import dev.tesserakt.sparql.Bindings
import dev.tesserakt.sparql.QueryStatistics
import dev.tesserakt.sparql.evaluation.DeferredOngoingQueryEvaluation
import dev.tesserakt.sparql.queryDeferred
import dev.tesserakt.testing.Test
import dev.tesserakt.testing.runTest
import sparql.ExternalQueryExecution
import kotlin.time.Duration
import kotlin.time.measureTime

class DeferredClearTest(
    query: String,
    store: Store
) : QueryExecutionTest(query, store) {

    override suspend fun test() = runTest {
        val input = ObservableStore(store)
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

        val ongoing: DeferredOngoingQueryEvaluation<Bindings>
        val setupTime = measureTime {
            ongoing = input.queryDeferred(query)
            // making sure the results are actually obtained
            ongoing.results
        }
        // checking the initial state (all data)
        builder.add(
            self = setupTime to ongoing.results.toList(),
            reference = reference(),
            strictOrdering = hasStrictOrdering,
            statistics = ongoing.stats()
        )
        // enqueuing all data as deletions
        input.clear()
        // checking again
        val current: List<Bindings>
        val elapsedTime = measureTime {
            current = ongoing.results.toList()
        }
        builder.add(
            self = elapsedTime to current,
            reference = reference(),
            strictOrdering = hasStrictOrdering,
            statistics = ongoing.stats()
        )
        builder.build()
    }

    override fun toString(): String =
        "Incremental (deferred) update SPARQL output comparison test\n * Query: `${
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
                statistics: QueryStatistics,
                strictOrdering: Boolean,
            ) {
                list.add(
                    compare(
                        received = self.second,
                        elapsedTime = self.first,
                        expected = reference.second,
                        strictOrdering = strictOrdering,
                        referenceTime = reference.first,
                        statistics = statistics
                    )
                )
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
                            appendLine("Comparison failed as initial state")
                        }
                        else -> {
                            // clear failed
                            appendLine("Comparison failed after full clear")
                        }
                    }
                    append(outputs[index].exceptionOrNull()?.message ?: "Detailed contents unavailable")
                }
            )
        }

        override fun toString(): String {
            return buildString {
                append(" * Incremental (deferred) clear time characteristics\n\tinsertion: ${outputs[0].elapsedTime}, deletion: ${outputs[1].elapsedTime}")
            }
        }

    }

}
