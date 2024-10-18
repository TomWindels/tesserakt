package sparql.types

import dev.tesserakt.rdf.types.Store
import dev.tesserakt.sparql.Compiler.Default.asSPARQLSelectQuery
import dev.tesserakt.sparql.runtime.common.types.Bindings
import dev.tesserakt.sparql.runtime.common.util.Debug
import dev.tesserakt.sparql.runtime.incremental.query.IncrementalQuery.Companion.query
import dev.tesserakt.util.toTruncatedString
import sparql.ExternalQueryExecution
import test.suite.Test
import test.suite.runTest
import kotlin.time.Duration
import kotlin.time.measureTime

data class OutputComparisonTest(
    val query: String,
    val store: Store
) : Test {

    override suspend fun test() = runTest {
        val actual: List<Bindings>
        Debug.reset()
        val elapsedTime = measureTime {
            actual = store.query(query.asSPARQLSelectQuery())
        }
        val external = ExternalQueryExecution(query, store)
        val expected: List<Bindings>
        val referenceTime = measureTime {
            expected = external.execute()
        }
        Result.from(
            received = actual,
            expected = expected,
            elapsedTime = elapsedTime,
            referenceTime = referenceTime,
            debugInformation = "${Debug.report()}${external.report()}"
        )
    }

    override fun toString(): String =
        "Incremental SPARQL output comparison test\n * Query: `${
            query.replace(Regex("\\s+"), " ").trim()
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
                AssertionError("Received results do not match expectations!\n$this\n * The following ${leftOver.size} binding(s) are superfluous:\n\t$leftOver\n * The following ${missing.size} binding(s) are missing:\n\t$missing\n")
            } else null
        }

        override fun toString(): String {
            return " * Got ${received.size} binding(s) ($elapsedTime):\n\t${received.toTruncatedString(500)}\n * Expected ${expected.size} binding(s) ($referenceTime):\n\t${expected.toTruncatedString(500)}\n * $debugInformation"
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

/**
 * Returns the diff of the two series of bindings. Ideally, the returned list is empty
 */
private fun compare(
    received: List<Bindings>,
    expected: List<Bindings>,
    elapsedTime: Duration,
    referenceTime: Duration,
    debugInformation: String
): OutputComparisonTest.Result {
    val leftOver = received.toMutableList()
    val missing = mutableListOf<Bindings>()
    expected.forEach { bindings ->
        if (!leftOver.removeFirst { it == bindings }) {
            missing.add(bindings)
        }
    }
    return OutputComparisonTest.Result(
        received = received,
        expected = expected,
        leftOver = leftOver,
        missing = missing,
        elapsedTime = elapsedTime,
        referenceTime = referenceTime,
        debugInformation = debugInformation
    )
}

private inline fun <T> MutableList<T>.removeFirst(predicate: (T) -> Boolean): Boolean {
    val it = listIterator()
    while (it.hasNext()) {
        if (predicate(it.next())) {
            it.remove()
            return true
        }
    }
    return false
}
