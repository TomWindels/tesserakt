package dev.tesserakt.benchmarking

actual object RunContext {

    private val runtime = Runtime.getRuntime()

    actual fun onIterationFinished() {
        runtime.gc()
    }

}
