package dev.tesserakt.benchmarking

interface RunContext {

    fun onIterationFinished()

    object Default: RunContext {
        override fun onIterationFinished() {
            // nothing to do
        }
    }

    companion object: RunContext {

        var CURRENT: RunContext = Default

        override fun onIterationFinished() {
            CURRENT.onIterationFinished()
        }

    }

}
