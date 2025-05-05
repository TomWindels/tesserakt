package sparql.types

import bindingComparisonOf
import dev.tesserakt.rdf.types.Store
import dev.tesserakt.sparql.Bindings
import dev.tesserakt.testing.Test
import dev.tesserakt.testing.TestFilter
import dev.tesserakt.testing.testEnv
import kotlin.time.Duration

class TestBuilderEnv {

    var filter: TestFilter = TestFilter.Default
    val tests = mutableListOf<QueryExecutionTestValues>()

    fun using(store: Store) = TestBuilder(environment = this, store = store)

    fun test(mapper: (QueryExecutionTestValues) -> Test) = testEnv {
        filter = this@TestBuilderEnv.filter
        tests.forEach { add(mapper(it)) }
    }

}

class TestBuilder(private val environment: TestBuilderEnv, private val store: Store) {

    infix fun test(query: String) {
        environment.tests.add(QueryExecutionTestValues(query = query, store = store))
    }

}

inline fun tests(block: TestBuilderEnv.() -> Unit) = TestBuilderEnv().apply(block)

/**
 * Returns the diff of the two series of bindings. Ideally, the returned list is empty
 */
fun compare(
    received: List<Bindings>,
    expected: List<Bindings>,
    elapsedTime: Duration,
    referenceTime: Duration,
    debugInformation: String
): OutputComparisonTest.Result {
    val comparison = bindingComparisonOf(expected, received)
    return OutputComparisonTest.Result(
        received = received,
        expected = expected,
        leftOver = comparison.leftOver,
        missing = comparison.missing,
        elapsedTime = elapsedTime,
        referenceTime = referenceTime,
        debugInformation = debugInformation
    )
}
