package sparql.util

import comunica.comunicaSelectQuery
import dev.tesserakt.rdf.types.Store
import dev.tesserakt.sparql.Compiler.Default.asSPARQLSelectQuery
import dev.tesserakt.sparql.runtime.common.types.Bindings
import dev.tesserakt.sparql.runtime.incremental.query.IncrementalQuery.Companion.query
import test.Test

data class OutputComparisonTest(
    val query: String,
    val store: Store
): Test {

    override suspend fun test(): Result<Unit> = runCatching {
        val results1 = store.query(query.asSPARQLSelectQuery())
        val results2 = store.comunicaSelectQuery(query)
        val diff = compare(results1, results2)
        if (diff.isNotEmpty()) {
            console.log("Raw outputs:\n\t$results1\n\t$results2")
        }
        diff.assertIsEmpty()
    }

    override fun toString(): String =
        "Incremental SPARQL output comparison test\n\tQuery: `${query.replace(Regex("\\s+"), " ").trim()}`\n\tInput: store with ${store.size} quad(s)"

}

data class ComparisonResult(
    val leftOver: List<Bindings>,
    val missing: List<Bindings>
) {
    fun isNotEmpty() = leftOver.isNotEmpty() || missing.isNotEmpty()
    fun assertIsEmpty() {
        if (isNotEmpty()) {
            throw AssertionError("Comparison failed!\n * The following ${leftOver.size} binding(s) are superfluous:\n\t$leftOver\n * The following ${missing.size} binding(s) are missing:\n\t$missing\n")
        }
    }
}

/**
 * Returns the diff of the two series of bindings. Ideally, the returned list is empty
 */
private fun compare(a: List<Bindings>, b: List<Bindings>): ComparisonResult {
    val diff1 = a.toMutableList()
    val diff2 = mutableListOf<Bindings>()
    b.forEach { bindings ->
        if (!diff1.removeFirst { it == bindings }) {
            diff2.add(bindings)
        }
    }
    return ComparisonResult(leftOver = diff1, missing = diff2)
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
