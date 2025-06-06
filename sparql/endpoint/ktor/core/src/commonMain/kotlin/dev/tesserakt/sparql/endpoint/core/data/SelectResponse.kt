package dev.tesserakt.sparql.endpoint.core.data

import dev.tesserakt.rdf.ontology.XSD
import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.sparql.Bindings
import dev.tesserakt.sparql.toBindings
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// https://www.w3.org/TR/2013/REC-sparql11-results-json-20130321/
@Serializable
data class SelectResponse(
    val head: Head,
    val results: Results,
) {

    @Serializable
    data class Head(
        @SerialName("vars")
        val variables: Collection<String>
    )

    @Serializable
    data class Results(
        val bindings: List<Map<String, Map<String, String>>>
    ) {

        companion object {

            fun Quad.Element.encoded(): Map<String, String> = when (this) {
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

            fun Map<String, String>.decoded(): Quad.Element = when (this["type"]) {
                "uri" -> {
                    val value = this["value"]
                    if (value.isNullOrBlank()) {
                        Quad.DefaultGraph
                    } else {
                        Quad.NamedTerm(value)
                    }
                }

                "literal" -> {
                    this["xml:lang"]
                        ?.let { lang -> Quad.LangString(value = this["value"]!!, language = lang) }
                        ?: Quad.Literal(
                            value = this["value"]!!,
                            type = this["datatype"]?.let { Quad.NamedTerm(it) } ?: XSD.string
                        )
                }

                "bnode" -> {
                    val id = this["value"]!!.filter { it.isDigit() }.toInt()
                    Quad.BlankTerm(id)
                }

                else -> throw IllegalArgumentException("Invalid encoded binding: $this")
            }

        }

        fun toBindings(): List<Bindings> {
            return bindings.map { entry -> entry.mapValues { it.value.decoded() }.toBindings() }
        }

    }

    fun toBindings(): List<Bindings> {
        return results.toBindings()
    }

}
