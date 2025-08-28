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
        var memoryProfiling = false

        override fun onIterationFinished() {
            CURRENT.onIterationFinished()
        }

        fun hasMemoryProfilingEnabled(): Boolean {
            return memoryProfiling
        }

    }

}
