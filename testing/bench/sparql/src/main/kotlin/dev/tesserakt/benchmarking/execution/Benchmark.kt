package dev.tesserakt.benchmarking.execution

import dev.tesserakt.benchmarking.EvaluationConfig
import dev.tesserakt.benchmarking.OutputWriter
import dev.tesserakt.benchmarking.execution.regular.RegularEvaluationConfig
import dev.tesserakt.benchmarking.execution.regular.RegularRunner
import dev.tesserakt.benchmarking.execution.replay.ReplayEvaluationConfig
import dev.tesserakt.benchmarking.execution.replay.ReplayRunner
import dev.tesserakt.benchmarking.execution.update.UpdateEvaluationConfig
import dev.tesserakt.benchmarking.execution.update.UpdateRunner
import dev.tesserakt.benchmarking.report.CliRunReporter
import dev.tesserakt.benchmarking.report.RunReporter
import dev.tesserakt.benchmarking.writeMetadata

class Benchmark<C: EvaluationConfig>(
    val evaluations: List<C>,
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
        abstract fun create(evaluation: EvaluationConfig): Runner

        object Default: RunnerFactory() {
            override fun create(evaluation: EvaluationConfig): Runner {
                return when (evaluation) {
                    is RegularEvaluationConfig -> RegularRunner(evaluation)
                    is ReplayEvaluationConfig -> ReplayRunner(evaluation)
                    is UpdateEvaluationConfig -> UpdateRunner(evaluation)
                    else -> throw RuntimeException("Unknown evaluation type: `${evaluation::class.simpleName}`")
                }
            }
        }
    }

    suspend fun run() {
        // looping the first time w/ the ability to output metadata alongside the evaluation
        evaluations.forEach { evaluation ->
            // creating a copy of the evaluation, with the new index supplied
            val output = OutputWriter(evaluation)
            val runner = factory.create(evaluation)
            runner.output = output
            runner.reporter = CliRunReporter(evaluation)
            // writing it at the root of the evaluation instead of the "with index" variant;
            // we only write it at this point, after `output.create`, to ensure the path exists
            writeMetadata(directory = evaluation.outputDirPath, evaluation)
            output.use {
                runner.run()
            }
        }
    }

}
