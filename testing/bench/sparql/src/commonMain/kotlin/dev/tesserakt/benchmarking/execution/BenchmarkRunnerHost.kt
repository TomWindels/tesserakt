package dev.tesserakt.benchmarking.execution

import dev.tesserakt.benchmarking.OutputWriter
import dev.tesserakt.benchmarking.execution.growing.GrowingRunner
import dev.tesserakt.benchmarking.execution.growing.GrowingRunnerEvaluation
import dev.tesserakt.benchmarking.execution.regular.RegularRunner
import dev.tesserakt.benchmarking.execution.regular.RegularRunnerEvaluation
import dev.tesserakt.benchmarking.execution.replay.ReplayRunner
import dev.tesserakt.benchmarking.execution.replay.ReplayRunnerEvaluation
import dev.tesserakt.benchmarking.report.CliRunReporter
import dev.tesserakt.benchmarking.report.RunReporter
import dev.tesserakt.benchmarking.writeMetadata

class BenchmarkRunnerHost(
    val executions: Int,
    val evaluations: List<Evaluation>,
    private val factory: RunnerFactory = RunnerFactory.Default,
) {

    abstract class Runner {

        lateinit var reporter: RunReporter
            internal set

        lateinit var output: OutputWriter
            internal set

        abstract suspend fun run()

    }

    abstract class RunnerFactory {
        abstract fun create(evaluation: Evaluation): Runner

        object Default: RunnerFactory() {
            override fun create(evaluation: Evaluation): Runner {
                return when (evaluation) {
                    is RegularRunnerEvaluation -> RegularRunner(evaluation)
                    is ReplayRunnerEvaluation -> ReplayRunner(evaluation)
                    is GrowingRunnerEvaluation -> GrowingRunner(evaluation)
                    else -> throw RuntimeException("Unknown evaluation type: `${evaluation::class.simpleName}`")
                }
            }
        }
    }

    suspend fun run() {
        if (executions <= 0) {
            // shouldn't be happening, but guarding just in case
            return
        }
        // looping the first time w/ the ability to output metadata alongside the evaluation
        evaluations.forEach { src ->
            // creating a copy of the evaluation, with the new index supplied
            val evaluation = src.withIndex(0)
            val output = OutputWriter(evaluation)
            val runner = factory.create(evaluation)
            runner.output = output
            runner.reporter = CliRunReporter(evaluation)
            output.create()
            // writing it at the root of the evaluation instead of the "with index" variant;
            // we only write it at this point, after `output.create`, to ensure the path exists
            writeMetadata(directory = src.outputDirPath, src)
            output.use {
                runner.run()
            }
        }
        // all subsequent iterations can do the exact same thing, w/o writing the metadata
        repeat(executions - 1) { index ->
            // skipping the very first iteration
            val index = index + 1
            evaluations.forEach { evaluation ->
                // creating a copy of the evaluation, with the new index supplied
                val evaluation = evaluation.withIndex(index)
                val output = OutputWriter(evaluation)
                val runner = factory.create(evaluation)
                runner.output = output
                runner.reporter = CliRunReporter(evaluation)
                output.create()
                output.use {
                    runner.run()
                }
            }
        }
    }

}
