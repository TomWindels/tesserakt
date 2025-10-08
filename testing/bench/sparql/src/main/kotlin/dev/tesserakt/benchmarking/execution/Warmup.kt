package dev.tesserakt.benchmarking.execution

import dev.tesserakt.benchmarking.Endpoint
import dev.tesserakt.benchmarking.EvaluationConfig

suspend fun doWarmup(endpoint: Endpoint, warmup: EvaluationConfig.Warmup) {
    repeat(warmup.runs) {
        warmup.queries.forEach { query ->
            endpoint.toRunner(query).use { evaluator ->
                evaluator.eval()
            }
        }
    }

}
