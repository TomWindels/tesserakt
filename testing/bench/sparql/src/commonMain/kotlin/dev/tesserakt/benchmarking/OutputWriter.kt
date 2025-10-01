package dev.tesserakt.benchmarking

import dev.tesserakt.benchmarking.endpoint.EndpointEvaluator


@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
expect class OutputWriter(evaluation: EvaluationConfig): AutoCloseable {

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

    fun markOutputs(id: String, output: EndpointEvaluator.Output)

}
