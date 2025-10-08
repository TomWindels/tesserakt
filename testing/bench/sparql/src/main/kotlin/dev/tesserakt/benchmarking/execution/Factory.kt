package dev.tesserakt.benchmarking.execution

import dev.tesserakt.benchmarking.Endpoint
import dev.tesserakt.benchmarking.endpoint.EndpointEvaluator

fun Endpoint.toRunner(query: String): EndpointEvaluator {
    return EndpointEvaluator(
        queryUrl = queryUrl,
        updateUrl = if (this is Endpoint.Mutable) updateUrl else null,
        token = if (this is Endpoint.Mutable)  token else null,
        query = query
    )
}
