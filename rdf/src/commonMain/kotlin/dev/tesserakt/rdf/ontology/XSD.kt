@file:Suppress("unused")

package dev.tesserakt.rdf.ontology

import dev.tesserakt.rdf.types.Quad.Companion.asNamedTerm

object XSD: Ontology {

    override val prefix = "xsd"
    override val base_uri = "http://www.w3.org/2001/XMLSchema#"

    val string = "${base_uri}string".asNamedTerm()
    val boolean = "${base_uri}boolean".asNamedTerm()
    val int = "${base_uri}int".asNamedTerm()
    val long = "${base_uri}long".asNamedTerm()
    val float = "${base_uri}float".asNamedTerm()
    val double = "${base_uri}double".asNamedTerm()
    val duration = "${base_uri}duration".asNamedTerm()
    val dateTime = "${base_uri}dateTime".asNamedTerm()
    val time = "${base_uri}time".asNamedTerm()
    val date = "${base_uri}date".asNamedTerm()

}
