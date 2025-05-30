package dev.tesserakt.benchmarking

object EvaluatorFactory {

    val implementations = listOf(SELF_IMPL) + references.keys

    private fun getFactory(evaluatorName: String) = when (evaluatorName) {
        SELF_IMPL -> { query: String -> Self(query) }
        in references -> { query: String -> references[evaluatorName]!!.invoke(query) }
        else -> throw IllegalArgumentException("Unknown evaluator: `${evaluatorName}`\nValid evaluators: ${implementations.joinToString { "\"$it\"" }}")
    }

    fun createEvaluator(evaluatorName: String, query: String) = getFactory(evaluatorName)(query)

    fun createEvaluator(runnerEvaluation: RunnerEvaluation) = createEvaluator(
        evaluatorName = runnerEvaluation.evaluatorName,
        query = runnerEvaluation.query
    )

}
