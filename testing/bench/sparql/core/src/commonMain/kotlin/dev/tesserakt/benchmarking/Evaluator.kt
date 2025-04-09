package dev.tesserakt.benchmarking

import dev.tesserakt.rdf.types.SnapshotStore


sealed class Evaluator : AutoCloseable {

    /**
     * An evaluation result, representing the number of [added] and [removed] bindings compared to the previous state,
     *  as well as a [checksum] value representing the contents
     */
    data class Output(
        val added: Int,
        val removed: Int,
        val checksum: Int,
    )

    /**
     * Prepares a diff to be evaluated, w/o actually evaluating it
     */
    abstract fun prepare(diff: SnapshotStore.Diff)

    /**
     * Evaluates the diff; this is the method which execution time matters!
     */
    abstract suspend fun eval()

    /**
     * Evaluates the result difference after applying the diff
     */
    abstract fun finish(): Output

    override fun close() {
        /* nothing to do */
    }
}
