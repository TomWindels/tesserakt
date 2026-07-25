package sparql.types

import dev.tesserakt.rdf.types.Store
import dev.tesserakt.sparql.Bindings
import dev.tesserakt.sparql.QueryStatistics
import dev.tesserakt.sparql.queryWithStatistics
import dev.tesserakt.testing.Test
import dev.tesserakt.testing.runTest
import dev.tesserakt.util.toTruncatedString
import sparql.ExternalQueryExecution
import kotlin.time.Duration
import kotlin.time.measureTime

class OutputComparisonTest(
    query: String,
    store: Store
) : QueryExecutionTest(query, store) {


    override suspend fun test() = runTest {
        val actual: List<Bindings>
        val statistics: QueryStatistics
        val elapsedTime = measureTime {
            val result = store.queryWithStatistics(query, granularity = QueryStatistics.Granularity.DETAILED)
            actual = result.first
            statistics = result.second
        }
        val external = ExternalQueryExecution(queryString, store)
        val expected: List<Bindings>
        val referenceTime = measureTime {
            try {
                expected = external.execute()
            } catch (t: Throwable) {
                return Test.Result.Failure(RuntimeException("Failed to use external implementation reference: ${t.message}", t))
            }
        }
        Result.from(
            received = actual,
            expected = expected,
            elapsedTime = elapsedTime,
            referenceTime = referenceTime,
            strictOrdering = hasStrictOrdering,
            statistics = statistics,
        )
    }

    override fun toString(): String =
        "Incremental SPARQL output comparison test\n * Query: `${
            queryString.replace(Regex("\\s+"), " ").trim()
        }`\n * Input: store with ${store.size} quad(s)"

    data class Result(
        val received: List<Bindings>,
        val expected: List<Bindings>,
        val leftOver: List<Bindings>,
        val missing: List<Bindings>,
        val elapsedTime: Duration,
        val referenceTime: Duration,
        val statistics: QueryStatistics
    ) : Test.Result {

        fun isNotEmpty() = leftOver.isNotEmpty() || missing.isNotEmpty()

        override fun isSuccess() = !isNotEmpty()

        override fun exceptionOrNull(): Throwable? {
            return if (isNotEmpty()) {
                AssertionError("Received results do not match expectations!\n$this\n * The following ${leftOver.size} binding(s) are superfluous:\n\t${leftOver.toTruncatedString(500)}\n * The following ${missing.size} binding(s) are missing:\n\t${missing.toTruncatedString(500)}\n")
            } else null
        }

        override fun toString(): String = buildString {
            append(" * Got ")
            append(received.size)
            append(" binding(s) (")
            append(elapsedTime)
            append("):\n\t")
            append(received.toTruncatedString(500))
            append("\n * Expected ")
            append(expected.size)
            append(" binding(s) (")
            append(referenceTime)
            append("):\n\t")
            appendLine(expected.toTruncatedString(500))
            append(statistics)
        }

        companion object {

            fun from(
                received: List<Bindings>,
                expected: List<Bindings>,
                elapsedTime: Duration,
                referenceTime: Duration,
                strictOrdering: Boolean,
                statistics: QueryStatistics,
            ): Result = compare(
                received = received,
                expected = expected,
                elapsedTime = elapsedTime,
                referenceTime = referenceTime,
                strictOrdering = strictOrdering,
                statistics = statistics
            )
        }

    }

}
