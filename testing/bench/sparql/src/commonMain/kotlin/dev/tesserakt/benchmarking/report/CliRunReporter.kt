package dev.tesserakt.benchmarking.report

import dev.tesserakt.benchmarking.EvaluationStage
import dev.tesserakt.benchmarking.execution.Evaluation
import kotlin.math.roundToInt
import kotlin.time.TimeSource

private const val DOT_LENGTH = 5

class CliRunReporter(private val run: Evaluation): RunReporter {

    private var dots = 0
    private var start: TimeSource.Monotonic.ValueTimeMark? = null
    private var state: EvaluationStage? = null

    override fun onStageChanged(stage: EvaluationStage) {
        if (state != null) {
            println(" ok, took ${start?.elapsedNow()}")
        } else {
            print(run.evaluatorId)
            print(", ")
            println(run.name)
        }
        if (stage != EvaluationStage.FINISHED) {
            print(" > ")
            print(stage.displayName)
        }
        state = stage
        start = TimeSource.Monotonic.markNow()
    }

    override fun onStageProgressed(progress: Float) {
        val target = (progress * DOT_LENGTH).roundToInt()
        if (target > dots) {
            print(".".repeat(target - dots))
            dots = target
        }
    }

    override fun onStageFailed(reason: Throwable) {
        println(" fail, took ${start?.elapsedNow()}")
        reason.printStackTrace()
    }

    private val EvaluationStage.displayName: String
        get() = when (this) {
            EvaluationStage.PREPARATION -> "preparation"
            EvaluationStage.EVALUATION -> "evaluation"
            EvaluationStage.FINISHED -> ""
        }

}
