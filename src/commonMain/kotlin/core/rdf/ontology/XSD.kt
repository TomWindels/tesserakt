package core.rdf.ontology

import core.rdf.types.Triple.Companion.asNamedNode

object XSD: Ontology {

    override val prefix = "xsd"
    override val base_uri = "https://www.w3.org/2001/XMLSchema#"

    val string = "${base_uri}string".asNamedNode()
    val boolean = "${base_uri}boolean".asNamedNode()
    val int = "${base_uri}int".asNamedNode()
    val long = "${base_uri}long".asNamedNode()
    val float = "${base_uri}float".asNamedNode()
    val double = "${base_uri}double".asNamedNode()
    val duration = "${base_uri}duration".asNamedNode()
    val dateTime = "${base_uri}dateTime".asNamedNode()
    val time = "${base_uri}time".asNamedNode()
    val date = "${base_uri}date".asNamedNode()

}
