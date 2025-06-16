package dev.tesserakt.benchmarking.execution

abstract class Evaluation {

    abstract val name: String
    abstract val query: String
    abstract val evaluatorName: String
    abstract val outputDirPath: String

    abstract fun metadata(): String

}
