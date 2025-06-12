package dev.tesserakt.sparql.endpoint.server

import dev.tesserakt.sparql.endpoint.core.data.SelectResponse
import dev.tesserakt.sparql.endpoint.core.data.UpdateRequest


interface SparqlEndpoint {

    suspend fun onSelectQueryRequest(query: String): Result<SelectResponse>

    suspend fun onUpdateQueryRequest(request: UpdateRequest): Result<Unit>

}
