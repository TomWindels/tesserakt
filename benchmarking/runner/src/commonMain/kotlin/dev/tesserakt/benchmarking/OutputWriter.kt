package dev.tesserakt.benchmarking


@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
expect class OutputWriter(config: RunnerConfig): AutoCloseable {

    /**
     * Called when the benchmark has been started, just before the very first call to [markStart]
     */
    fun create()

    /**
     * Called on every new run start
     */
    fun reset()

    /**
     * Called when the benchmark has finished, just after the very last call to [markEnd]
     */
    override fun close()

    /**
     * Notification about the start of an execution for the given [id]
     */
    fun markStart(id: String)

    /**
     * Notification about the end of an execution for the given [id]
     */
    fun markEnd(id: String)

    fun markOutputs(id: String, output: Evaluator.Output)

}
