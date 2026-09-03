package dev.tesserakt.concurrent

expect sealed class ConcurrencyMode() {

    internal abstract fun toTaskRunner(): TaskRunner

    companion object

}

/**
 * Configures the default [ConcurrencyMode] that should be used in supported structures.
 */
fun ConcurrencyMode.Companion.set(mode: ConcurrencyMode) {
    globalTaskRunner = mode.toTaskRunner()
}

object SingleThreaded : ConcurrencyMode() {

    override fun toTaskRunner(): TaskRunner {
        return TaskRunner.SingleThreaded
    }

}
