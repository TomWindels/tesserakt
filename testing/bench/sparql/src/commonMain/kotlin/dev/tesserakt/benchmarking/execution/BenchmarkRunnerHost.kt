package dev.tesserakt.benchmarking.execution

import dev.tesserakt.benchmarking.OutputWriter
import dev.tesserakt.benchmarking.execution.regular.RegularRunner
import dev.tesserakt.benchmarking.execution.regular.RegularRunnerEvaluation
import dev.tesserakt.benchmarking.execution.replay.ReplayRunner
import dev.tesserakt.benchmarking.execution.replay.ReplayRunnerEvaluation
import dev.tesserakt.benchmarking.report.CliRunReporter
import dev.tesserakt.benchmarking.report.RunReporter

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
                    else -> throw RuntimeException("Unknown evaluation type: `${evaluation::class.simpleName}`")
                }
            }
        }
    }

    suspend fun run() {
        repeat(executions) { index ->
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
