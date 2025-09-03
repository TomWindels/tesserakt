package dev.tesserakt.benchmarking

import dev.tesserakt.benchmarking.execution.Evaluation

object EvaluatorFactory {

    val implementations = listOf(SELF_IMPL) + references.keys

    private fun getFactoryPreferIncremental(evaluatorId: EvaluatorId) = when (evaluatorId) {
        is EvaluatorId.Endpoint -> { query: String ->
            EndpointImplementation(
                queryUrl = evaluatorId.queryUrl,
                updateUrl = if (evaluatorId is EvaluatorId.Endpoint.Mutable) evaluatorId.updateUrl else null,
                token = if (evaluatorId is EvaluatorId.Endpoint.Mutable) evaluatorId.token else null,
                query = query
            )
        }

        SELF_IMPL -> { query: String ->
            SelfIncremental(query)
        }

        in references -> { query: String ->
            references[evaluatorId]!!.invoke(query)
        }

        else -> throw IllegalArgumentException("Unknown evaluator: `${evaluatorId}`\nValid evaluators: ${implementations.joinToString { "\"$it\"" }}")
    }

    private fun getFactoryPreferRegular(evaluatorId: EvaluatorId) = when (evaluatorId) {
        is EvaluatorId.Endpoint -> { query: String ->
            EndpointImplementation(
                queryUrl = evaluatorId.queryUrl,
                updateUrl = if (evaluatorId is EvaluatorId.Endpoint.Mutable) evaluatorId.updateUrl else null,
                token = if (evaluatorId is EvaluatorId.Endpoint.Mutable) evaluatorId.token else null,
                query = query
            )
        }

        SELF_IMPL -> { query: String ->
            SelfRegular(query)
        }

        in references -> { query: String ->
            references[evaluatorId]!!.invoke(query)
        }

        else -> throw IllegalArgumentException("Unknown evaluator: `${evaluatorId}`\nValid evaluators: ${implementations.joinToString { "\"$it\"" }}")
    }

    fun createEvaluatorPreferIncremental(evaluatorId: EvaluatorId, query: String) =
        getFactoryPreferIncremental(evaluatorId)(query)

    fun createEvaluatorPreferIncremental(evaluation: Evaluation) = createEvaluatorPreferIncremental(
        evaluatorId = evaluation.evaluatorId,
        query = evaluation.query
    )

    fun createEvaluatorPreferRegular(evaluatorId: EvaluatorId, query: String) =
        getFactoryPreferRegular(evaluatorId)(query)

    fun createEvaluatorPreferRegular(evaluation: Evaluation) = createEvaluatorPreferRegular(
        evaluatorId = evaluation.evaluatorId,
        query = evaluation.query
    )

}
