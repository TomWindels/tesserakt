package test.suite

import dev.tesserakt.rdf.types.Store
import dev.tesserakt.util.printerrln
import sparql.types.OutputComparisonTest

class TestEnvironment {

    private val tests = mutableListOf<Test>()

    fun using(store: Store) = TestBuilder(store = store)

    inner class TestBuilder(private val store: Store) {

        infix fun test(query: String) {
            tests.add(OutputComparisonTest(query = query, store = store))
        }

    }

    data class Results(
        val results: List<Pair<Test, Test.Result>>,
        val summary: String = results.summary()
    ) {

        companion object {
            private fun List<Pair<Test, Test.Result>>.summary() = "${count { it.second.isSuccess() }} / $size test(s) succeeded!"
        }

        fun report() {
            println(summary)
            results.forEachIndexed { i, (test, result) ->
                if (result.isSuccess()) {
                    println("Test ${i + 1} / ${results.size} succeeded!")
                    println(result)
                } else {
                    printerrln("Test ${i + 1} / ${results.size} failed!")
                    printerrln("Test setup:\n$test")
                    val reason = result.exceptionOrNull() ?: return@forEachIndexed
                    if (reason is AssertionError) {
                        printerrln("Assertion failed: ${reason.message}")
                    } else {
                        printerrln("Unexpected `${reason::class.simpleName}` was thrown!")
                        reason.printStackTrace()
                    }
                }
            }
        }

    }

    suspend fun run() = Results(results = tests.map { it to it.test() })

}
