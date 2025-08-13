package dev.tesserakt.benchmarking

object GcContext: RunContext {

    private val runtime = Runtime.getRuntime()

    override fun onIterationFinished() {
        runtime.gc()
    }

}
