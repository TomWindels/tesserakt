package sparql.types

import dev.tesserakt.rdf.types.Store
import dev.tesserakt.sparql.Bindings
import dev.tesserakt.sparql.query
import dev.tesserakt.sparql.runtime.RuntimeStatistics
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
        val elapsedTime = measureTime {
            actual = store.query(query)
        }
        val external = ExternalQueryExecution(queryString, store.toSet())
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
            debugInformation = "${RuntimeStatistics.report()}${external.report()}"
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
        val debugInformation: String
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
            append(expected.toTruncatedString(500))
            if (debugInformation.isNotBlank()) {
                append("\n * ")
                append(debugInformation)
            }
        }

        companion object {

            fun from(
                received: List<Bindings>,
                expected: List<Bindings>,
                elapsedTime: Duration,
                referenceTime: Duration,
                debugInformation: String
            ): Result = compare(
                received = received,
                expected = expected,
                elapsedTime = elapsedTime,
                referenceTime = referenceTime,
                debugInformation = debugInformation
            )
        }

    }

}
