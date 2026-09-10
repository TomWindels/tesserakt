package dev.tesserakt.rdf.ontology

import dev.tesserakt.rdf.types.Quad.NamedTerm

object RDF: Ontology {

    override val prefix = "rdf"
    override val base_uri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#"

    val type = NamedTerm("${base_uri}type")
    val first = NamedTerm("${base_uri}first")
    val rest = NamedTerm("${base_uri}rest")
    val nil = NamedTerm("${base_uri}nil")
    val langString = NamedTerm("${base_uri}langString")

}
