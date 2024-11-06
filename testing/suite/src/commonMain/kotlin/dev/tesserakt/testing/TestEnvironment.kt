package dev.tesserakt.testing

import dev.tesserakt.util.printerrln

class TestEnvironment {

    private val tests = mutableListOf<Test>()

    fun add(test: Test) {
        tests.add(test)
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

    suspend fun run(count: Int = 1) = Results(
        results = (0..<count).flatMap { tests.map { it to it.test() } }
    )

}
