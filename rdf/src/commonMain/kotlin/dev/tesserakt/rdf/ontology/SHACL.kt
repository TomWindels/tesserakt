@file:Suppress("unused", "SpellCheckingInspection")

package dev.tesserakt.rdf.ontology

import dev.tesserakt.rdf.types.Quad.NamedTerm

object SHACL: Ontology {

    override val prefix = "sh"
    override val base_uri = "http://www.w3.org/ns/shacl#"

    val NodeShape = NamedTerm("${base_uri}NodeShape")
    val PropertyShape = NamedTerm("${base_uri}PropertyShape")
    val Literal = NamedTerm("${base_uri}Literal")
    val IRI = NamedTerm("${base_uri}IRI")

    val property = NamedTerm("${base_uri}property")
    val path = NamedTerm("${base_uri}path")
    val targetClass = NamedTerm("${base_uri}targetClass")
    val nodeKind = NamedTerm("${base_uri}nodeKind")
    val datatype = NamedTerm("${base_uri}datatype")
    val minCount = NamedTerm("${base_uri}minCount")
    val maxCount = NamedTerm("${base_uri}maxCount")

}
