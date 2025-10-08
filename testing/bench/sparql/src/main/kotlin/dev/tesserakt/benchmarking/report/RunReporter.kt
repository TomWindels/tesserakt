package dev.tesserakt.benchmarking.report

import dev.tesserakt.benchmarking.EvaluationStage

interface RunReporter {

    fun onStageChanged(stage: EvaluationStage)

    fun onStageProgressed(progress: Float)

    fun onStageFailed(reason: Throwable)

}
