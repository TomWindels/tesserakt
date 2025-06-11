package dev.tesserakt.sparql.endpoint.server.impl

import dev.tesserakt.sparql.Bindings
import dev.tesserakt.sparql.Query
import dev.tesserakt.sparql.endpoint.core.data.SelectResponse
import dev.tesserakt.sparql.endpoint.core.data.SelectResponse.Results.Companion.encoded
import dev.tesserakt.sparql.evaluation.DeferredOngoingQueryEvaluation
import dev.tesserakt.sparql.variables

internal fun SelectResponse(query: Query<Bindings>, evaluation: DeferredOngoingQueryEvaluation<Bindings>): SelectResponse {
    return SelectResponse(
        head = SelectResponse.Head(
            variables = query.variables
        ),
        results = SelectResponse.Results(
            bindings = evaluation.results.map { it.associate { it.first to it.second.encoded() } }
        )
    )
}

internal fun SelectResponse(query: Query<Bindings>, results: Collection<Bindings>): SelectResponse {
    return SelectResponse(
        head = SelectResponse.Head(
            variables = query.variables
        ),
        results = SelectResponse.Results(
            bindings = results.map { it.associate { it.first to it.second.encoded() } }
        )
    )
}
