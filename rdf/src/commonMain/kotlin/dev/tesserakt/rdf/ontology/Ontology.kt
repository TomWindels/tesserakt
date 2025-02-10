package dev.tesserakt.rdf.ontology

import dev.tesserakt.rdf.types.Quad

interface Ontology {

    val prefix: String
    val base_uri: String

    operator fun invoke(iri: String) = Quad.NamedTerm("${base_uri}$iri")

}
