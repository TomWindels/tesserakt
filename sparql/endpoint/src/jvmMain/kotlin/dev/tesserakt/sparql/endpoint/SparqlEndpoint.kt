package dev.tesserakt.sparql.endpoint

import dev.tesserakt.sparql.endpoint.data.SelectResponse


interface SparqlEndpoint {

    suspend fun onSelectQueryRequest(query: String): Result<SelectResponse>

    suspend fun onUpdateQueryRequest(query: String): Result<Unit>

}
