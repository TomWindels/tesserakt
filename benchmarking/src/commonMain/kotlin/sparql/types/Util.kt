package sparql.types

import dev.tesserakt.rdf.types.Store
import dev.tesserakt.sparql.runtime.common.types.Bindings
import dev.tesserakt.testing.Test
import dev.tesserakt.testing.testEnv
import kotlin.time.Duration

class TestBuilderEnv {

    val tests = mutableListOf<QueryExecutionTest>()

    fun using(store: Store) = TestBuilder(environment = this, store = store)

}

class TestBuilder(private val environment: TestBuilderEnv, private val store: Store) {

    infix fun test(query: String) {
        environment.tests.add(QueryExecutionTest(query = query, store = store))
    }

}

inline fun tests(block: TestBuilderEnv.() -> Unit) = TestBuilderEnv().apply(block).tests.toList()

inline fun List<QueryExecutionTest>.test(mapper: (QueryExecutionTest) -> Test) = testEnv { forEach { add(mapper(it)) } }

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
    val comparison = fastCompare(expected, received)
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
