package dev.tesserakt.benchmarking

import dev.tesserakt.benchmarking.execution.EndpointUtil
import dev.tesserakt.benchmarking.execution.Evaluation

object EvaluatorFactory {

    val implementations = listOf(SELF_IMPL) + references.keys

    private fun getFactoryPreferIncremental(evaluatorName: String) = when {
        evaluatorName.startsWith("endpoint") -> { query: String ->
            EndpointImplementation(endpoint = EndpointUtil.evaluatorNameToEndpointUrl(evaluatorName), query = query)
        }

        evaluatorName == SELF_IMPL -> { query: String ->
            SelfIncremental(query)
        }

        evaluatorName in references -> { query: String ->
            references[evaluatorName]!!.invoke(query)
        }

        else -> throw IllegalArgumentException("Unknown evaluator: `${evaluatorName}`\nValid evaluators: ${implementations.joinToString { "\"$it\"" }}")
    }

    private fun getFactoryPreferRegular(evaluatorName: String) = when {
        evaluatorName.startsWith("endpoint") -> { query: String ->
            EndpointImplementation(endpoint = EndpointUtil.evaluatorNameToEndpointUrl(evaluatorName), query = query)
        }

        evaluatorName == SELF_IMPL -> { query: String ->
            SelfRegular(query)
        }

        evaluatorName in references -> { query: String ->
            references[evaluatorName]!!.invoke(query)
        }

        else -> throw IllegalArgumentException("Unknown evaluator: `${evaluatorName}`\nValid evaluators: ${implementations.joinToString { "\"$it\"" }}")
    }

    fun createEvaluatorPreferIncremental(evaluatorName: String, query: String) = getFactoryPreferIncremental(evaluatorName)(query)

    fun createEvaluatorPreferIncremental(evaluation: Evaluation) = createEvaluatorPreferIncremental(
        evaluatorName = evaluation.evaluatorName,
        query = evaluation.query
    )

    fun createEvaluatorPreferRegular(evaluatorName: String, query: String) = getFactoryPreferRegular(evaluatorName)(query)

    fun createEvaluatorPreferRegular(evaluation: Evaluation) = createEvaluatorPreferRegular(
        evaluatorName = evaluation.evaluatorName,
        query = evaluation.query
    )

}
