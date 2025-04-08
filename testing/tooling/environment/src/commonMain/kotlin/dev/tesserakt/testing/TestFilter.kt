package dev.tesserakt.testing

fun interface TestFilter {

    object Default: TestFilter {
        override fun shouldSkip(test: Test): Boolean {
            return false
        }
    }

    fun shouldSkip(test: Test): Boolean

}
