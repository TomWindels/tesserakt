package core.rdf.ontology

import core.rdf.types.Triple.Companion.asNamedNode

object SHACL: Ontology {

    override val prefix = "sh"
    override val base_uri = "http://www.w3.org/ns/shacl#"

    val NodeShape = "${base_uri}NodeShape".asNamedNode()
    val PropertyShape = "${base_uri}PropertyShape".asNamedNode()
    val Literal = "${base_uri}Literal".asNamedNode()
    val IRI = "${base_uri}IRI".asNamedNode()

    val property = "${base_uri}property".asNamedNode()
    val path = "${base_uri}path".asNamedNode()
    val targetClass = "${base_uri}targetClass".asNamedNode()
    val nodeKind = "${base_uri}nodeKind".asNamedNode()
    val datatype = "${base_uri}datatype".asNamedNode()
    val minCount = "${base_uri}minCount".asNamedNode()
    val maxCount = "${base_uri}maxCount".asNamedNode()

}
