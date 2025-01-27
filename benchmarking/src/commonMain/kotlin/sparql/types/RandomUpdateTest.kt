package sparql.types

import dev.tesserakt.rdf.types.MutableStore
import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.rdf.types.Store
import dev.tesserakt.sparql.Compiler.Default.asSPARQLSelectQuery
import dev.tesserakt.sparql.runtime.common.types.Bindings
import dev.tesserakt.sparql.runtime.incremental.delta.Delta
import dev.tesserakt.sparql.runtime.incremental.evaluation.OngoingQueryEvaluation
import dev.tesserakt.sparql.runtime.incremental.evaluation.query
import dev.tesserakt.sparql.runtime.incremental.query.IncrementalSelectQuery
import dev.tesserakt.testing.Test
import dev.tesserakt.testing.runTest
import sparql.ExternalQueryExecution
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.measureTime

data class RandomUpdateTest(
    val query: String,
    val store: Store,
    val seed: Int = 1,
    val iterations: Int = store.size * 2
) : Test {

    private val deltas = buildList {
        // getting all delta's by simulating the changes to a hypothetical input store
        val temp = MutableStore()
        val random = Random(seed)
        repeat(iterations) {
            // getting the next delta
            val delta = getNextDelta(current = temp, source = store, random = random)
            temp.process(delta)
            add(delta)
        }
    }

    override suspend fun test() = runTest {
        val input = MutableStore()
        val builder = Result.Builder(query = query.asSPARQLSelectQuery(), store = store, deltas = deltas)
        suspend fun reference(): Pair<Duration, List<Bindings>> {
            val external = ExternalQueryExecution(query, input)
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
        val setupTime = measureTime {
            ongoing = input.query(query.asSPARQLSelectQuery())
        }
        // checking the initial state (no data)
        builder.add(
            self = setupTime to ongoing.results,
            reference = reference()
        )
        repeat(iterations) { i ->
            // and processing it
            val current: List<Bindings>
            val elapsedTime = measureTime {
                input.process(deltas[i])
                current = ongoing.results
            }
            builder.add(
                self = elapsedTime to current,
                reference = reference()
            )
        }
        builder.build()
    }

    override fun toString(): String =
        "Random update SPARQL output comparison test\n * Query: `${
            query.replace(Regex("\\s+"), " ").trim()
        }`\n * Input: store with ${store.size} quad(s)"

    data class Result(
        val store: Store,
        val query: IncrementalSelectQuery,
        val outputs: List<OutputComparisonTest.Result>,
        val deltas: List<Delta.Data>
    ) : Test.Result {

        class Builder(
            private val query: IncrementalSelectQuery,
            private val store: Store,
            private val deltas: List<Delta.Data>
        ) {

            private val list = ArrayList<OutputComparisonTest.Result>(store.size * 2)

            fun add(
                self: Pair<Duration, List<Bindings>>,
                reference: Pair<Duration, List<Bindings>>,
            ) {
                list.add(
                    compare(
                        received = self.second,
                        elapsedTime = self.first,
                        expected = reference.second,
                        referenceTime = reference.first,
                        debugInformation = ""
                    )
                )
            }

            fun build() = Result(store = store, outputs = list, deltas = deltas, query = query)

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

                        else -> {
                            // incremental change failed
                            appendLine("First failure occurred at incremental change #${index + 1}")
                        }
                    }
                    if (index >= 1) {
                        appendLine("Previous result:\n\t${outputs[index - 1].summary()}")
                    }
                    append(outputs[index].exceptionOrNull()?.message ?: "Detailed contents unavailable")
                    appendLine(" * Complete update sequence")
                    repeat(index) { i ->
                        append("     ")
                        appendLine(deltaSummary(i, deltas[i]))
                    }
                    append("  >> ")
                    appendLine(deltaSummary(index, deltas[index]))
                    // also replaying the entire store up until this point, so all contents used in the reference
                    //  query can be displayed
//                    appendLine(" * Contents:")
//                    val store = MutableStore().apply {
//                        repeat(index) { process(deltas[it]) }
//                    }
//                    append(store.toString().prependIndent("   "))
                }
            )
        }

        override fun toString(): String {
            val min = outputs.minOf { it.elapsedTime }
            val max = outputs.maxOf { it.elapsedTime }
            val mean = (outputs.sumOf { it.elapsedTime.inWholeNanoseconds } / outputs.size).nanoseconds
            return buildString {
                appendLine(" * ${outputs.count { it.isSuccess() }} / ${outputs.size} individual output(s) matched")
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

private fun getNextDelta(current: Set<Quad>, source: Set<Quad>, random: Random): Delta.Data {
    require(source.isNotEmpty()) { "Empty input data is not allowed!" }
    // biasing the type of delta based on the amount of triples currently present compared to the number of triples
    //  possible: if there aren't any triples currently present, then we guarantee an addition happening now, the
    //  opposite is also true
    val isAddition = random.nextFloat() >= (current.size / source.size.toFloat())
    // based on the type, we sample the next `random` item from what we have or what we can still get
    return if (isAddition) {
        val available = source - current
        Delta.DataAddition(available.random(random))
    } else {
        Delta.DataDeletion(current.random(random))
    }
}

private fun deltaSummary(index: Int, delta: Delta.Data): String =
    "#${(index + 1).toString().padEnd(3)} [${if (delta is Delta.DataAddition) '+' else '-'}] ${delta.value}"

private fun MutableStore.process(delta: Delta.Data) {
    when (delta) {
        is Delta.DataAddition -> add(delta.value)
        is Delta.DataDeletion -> remove(delta.value)
    }
}
