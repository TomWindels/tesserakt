package dev.tesserakt.benchmarking.execution

abstract class Evaluation {

    abstract val name: String
    abstract val query: String
    abstract val evaluatorName: String
    abstract val outputDirPath: String

    abstract fun metadata(): String

    /**
     * Returns a copy of this [Evaluation], using the [index] as part of the
     *  new [Evaluation]s [name] (and [outputDirPath])
     */
    abstract fun withIndex(index: Int): Evaluation

}