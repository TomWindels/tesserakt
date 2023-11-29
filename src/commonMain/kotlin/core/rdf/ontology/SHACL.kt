@file:Suppress("unused", "SpellCheckingInspection")

package core.rdf.ontology

import core.rdf.types.Triple.Companion.asNamedTerm

object SHACL: Ontology {

    override val prefix = "sh"
    override val base_uri = "http://www.w3.org/ns/shacl#"

    val NodeShape = "${base_uri}NodeShape".asNamedTerm()
    val PropertyShape = "${base_uri}PropertyShape".asNamedTerm()
    val Literal = "${base_uri}Literal".asNamedTerm()
    val IRI = "${base_uri}IRI".asNamedTerm()

    val property = "${base_uri}property".asNamedTerm()
    val path = "${base_uri}path".asNamedTerm()
    val targetClass = "${base_uri}targetClass".asNamedTerm()
    val nodeKind = "${base_uri}nodeKind".asNamedTerm()
    val datatype = "${base_uri}datatype".asNamedTerm()
    val minCount = "${base_uri}minCount".asNamedTerm()
    val maxCount = "${base_uri}maxCount".asNamedTerm()

}
