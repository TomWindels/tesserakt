package dev.tesserakt.sparql.endpoint.data

import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.sparql.Bindings
import dev.tesserakt.sparql.Query
import dev.tesserakt.sparql.evaluation.DeferredOngoingQueryEvaluation
import dev.tesserakt.sparql.variables
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// https://www.w3.org/TR/2013/REC-sparql11-results-json-20130321/
@Serializable
data class SelectResponse(
    val head: Head,
    val results: Results,
) {

    constructor(query: Query<Bindings>, evaluation: DeferredOngoingQueryEvaluation<Bindings>): this(
        head = Head(query),
        results = Results(evaluation)
    )

    @Serializable
    data class Head(
        @SerialName("vars")
        val variables: Collection<String>
    ) {
        constructor(query: Query<Bindings>): this(variables = query.variables)
    }

    @Serializable
    data class Results(
        val bindings: List<Map<String, Map<String, String>>>
    ) {
        constructor(evaluation: DeferredOngoingQueryEvaluation<Bindings>): this(
            bindings = evaluation.results.map { it.associate { it.first to it.second.encoded() } }
        )

        companion object {

            private fun Quad.Element.encoded(): Map<String, String> = when (this) {
                is Quad.NamedTerm -> mapOf(
                    "type" to "uri",
                    "value" to value
                )
                is Quad.Literal -> mapOf(
                    "type" to "literal",
                    "value" to value,
                    "datatype" to type.value
                )
                is Quad.LangString -> mapOf(
                    "type" to "literal",
                    "value" to value,
                    "xml:lang" to language
                )
                is Quad.BlankTerm -> mapOf(
                    "type" to "bnode",
                    "value" to id.toString()
                )
                Quad.DefaultGraph -> mapOf(
                    "type" to "uri",
                    "value" to ""
                )
            }

        }

    }

}
