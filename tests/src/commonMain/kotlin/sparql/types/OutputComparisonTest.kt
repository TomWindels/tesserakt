package sparql.types

import dev.tesserakt.rdf.types.Store
import dev.tesserakt.sparql.Compiler.Default.asSPARQLSelectQuery
import dev.tesserakt.sparql.runtime.common.types.Bindings
import dev.tesserakt.sparql.runtime.incremental.query.IncrementalQuery.Companion.query
import sparql.executeExternalQuery
import test.suite.Test
import test.suite.runTest

data class OutputComparisonTest(
    val query: String,
    val store: Store
) : Test {

    override suspend fun test() = runTest {
        val actual = store.query(query.asSPARQLSelectQuery())
        val expected = executeExternalQuery(query = query, data = store)
        Result.from(received = actual, expected = expected)
    }

    override fun toString(): String =
        "Incremental SPARQL output comparison test\n * Query: `${
            query.replace(Regex("\\s+"), " ").trim()
        }`\n * Input: store with ${store.size} quad(s)"

    data class Result(
        val received: List<Bindings>,
        val expected: List<Bindings>,
        val leftOver: List<Bindings>,
        val missing: List<Bindings>
    ) : Test.Result {

        fun isNotEmpty() = leftOver.isNotEmpty() || missing.isNotEmpty()

        override fun isSuccess() = !isNotEmpty()

        override fun exceptionOrNull(): Throwable? {
            return if (isNotEmpty()) {
                AssertionError("Received results do not match expectations!\n$this\n * The following ${leftOver.size} binding(s) are superfluous:\n\t$leftOver\n * The following ${missing.size} binding(s) are missing:\n\t$missing\n")
            } else null
        }

        override fun toString(): String {
            return " * Got ${received.size} binding(s):\n\t$received\n * Expected ${expected.size} bindings:\n\t$expected"
        }

        companion object {

            fun from(received: List<Bindings>, expected: List<Bindings>): Result {
                return compare(received, expected)
            }
        }

    }

}

/**
 * Returns the diff of the two series of bindings. Ideally, the returned list is empty
 */
private fun compare(received: List<Bindings>, expected: List<Bindings>): OutputComparisonTest.Result {
    val leftOver = received.toMutableList()
    val missing = mutableListOf<Bindings>()
    expected.forEach { bindings ->
        if (!leftOver.removeFirst { it == bindings }) {
            missing.add(bindings)
        }
    }
    return OutputComparisonTest.Result(received = received, expected = expected, leftOver = leftOver, missing = missing)
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
