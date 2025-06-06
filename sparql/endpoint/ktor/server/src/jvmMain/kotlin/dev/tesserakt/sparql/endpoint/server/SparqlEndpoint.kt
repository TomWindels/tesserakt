package dev.tesserakt.sparql.endpoint.server

import dev.tesserakt.sparql.endpoint.core.data.SelectResponse


interface SparqlEndpoint {

    suspend fun onSelectQueryRequest(query: String): Result<SelectResponse>

    suspend fun onUpdateQueryRequest(query: String): Result<Unit>

}
