package sparql.util

import dev.tesserakt.rdf.types.Store
import test.Test

class TestEnvironment {

    private val tests = mutableListOf<Test>()

    fun using(store: Store) = TestBuilder(store = store)

    inner class TestBuilder(private val store: Store) {

        infix fun test(query: String) {
            tests.add(OutputComparisonTest(query = query, store = store))
        }

    }

    data class Results(
        val results: List<Pair<Test, Throwable?>>,
        val summary: String = results.summary()
    ) {

        companion object {
            private fun List<Pair<Test, Throwable?>>.summary() = "${count { it.second == null }} / $size test(s) succeeded!"
        }

        fun report() {
            console.log(summary)
            results.forEachIndexed { i, (test, exception) ->
                if (exception == null) {
                    return@forEachIndexed
                }
                console.error("Test ${i + 1} / ${results.size} failed!")
                console.error("Test setup:\n$test")
                console.error("Cause:")
                exception.printStackTrace()
            }
        }

    }

    suspend fun run() = Results(results = tests.map { it to it.test().exceptionOrNull() })

    companion object {

        inline fun test(block: TestEnvironment.() -> Unit): TestEnvironment = TestEnvironment().apply(block)

    }

}
