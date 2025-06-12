package dev.tesserakt.benchmarking

import dev.tesserakt.benchmarking.EndpointConfig.Companion.evaluatorNameToEndpointUrl

object EvaluatorFactory {

    val implementations = listOf(SELF_IMPL) + references.keys

    private fun getFactory(evaluatorName: String) = when {
        evaluatorName.startsWith("endpoint") -> { query: String ->
            EndpointImplementation(endpoint = evaluatorNameToEndpointUrl(evaluatorName), query = query)
        }

        evaluatorName == SELF_IMPL -> { query: String ->
            Self(query)
        }

        evaluatorName in references -> { query: String ->
            references[evaluatorName]!!.invoke(query)
        }

        else -> throw IllegalArgumentException("Unknown evaluator: `${evaluatorName}`\nValid evaluators: ${implementations.joinToString { "\"$it\"" }}")
    }

    fun createEvaluator(evaluatorName: String, query: String) = getFactory(evaluatorName)(query)

    fun createEvaluator(runnerEvaluation: RunnerEvaluation) = createEvaluator(
        evaluatorName = runnerEvaluation.evaluatorName,
        query = runnerEvaluation.query
    )

}
