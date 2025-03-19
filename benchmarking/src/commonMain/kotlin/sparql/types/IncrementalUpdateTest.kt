package sparql.types

import dev.tesserakt.rdf.types.MutableStore
import dev.tesserakt.rdf.types.Store
import dev.tesserakt.sparql.Bindings
import dev.tesserakt.sparql.OngoingQueryEvaluation
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
        val input = MutableStore()
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
        builder.add(
            self = setupTime to ongoing.results.toList(),
            reference = reference(),
            debugInformation = ongoing.debugInformation()
        )
        // building it up
        store.forEach { quad ->
            val current: List<Bindings>
            val elapsedTime = measureTime {
                input.add(quad)
                current = ongoing.results.toList()
            }
            builder.add(
                self = elapsedTime to current,
                reference = reference(),
                debugInformation = ongoing.debugInformation()
            )
        }
        // breaking it back down
        store.forEach { quad ->
            val current: List<Bindings>
            val elapsedTime = measureTime {
                input.remove(quad)
                current = ongoing.results.toList()
            }
            builder.add(
                self = elapsedTime to current,
                reference = reference(),
                debugInformation = ongoing.debugInformation()
            )
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
                debugInformation: String,
            ) {
                list.add(
                    compare(
                        received = self.second,
                        elapsedTime = self.first,
                        expected = reference.second,
                        referenceTime = reference.first,
                        debugInformation = debugInformation
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
