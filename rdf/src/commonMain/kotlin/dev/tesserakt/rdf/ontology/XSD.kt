@file:Suppress("unused")

package dev.tesserakt.rdf.ontology

import dev.tesserakt.rdf.types.Quad.NamedTerm

object XSD: Ontology {

    override val prefix = "xsd"
    override val base_uri = "http://www.w3.org/2001/XMLSchema#"

    val string = NamedTerm("${base_uri}string")
    val boolean = NamedTerm("${base_uri}boolean")
    val byte = NamedTerm("${base_uri}byte")
    val short = NamedTerm("${base_uri}short")
    val int = NamedTerm("${base_uri}int")
    val integer = NamedTerm("${base_uri}integer")
    val long = NamedTerm("${base_uri}long")
    val float = NamedTerm("${base_uri}float")
    val double = NamedTerm("${base_uri}double")
    val decimal = NamedTerm("${base_uri}decimal")
    val duration = NamedTerm("${base_uri}duration")
    val dateTime = NamedTerm("${base_uri}dateTime")
    val time = NamedTerm("${base_uri}time")
    val date = NamedTerm("${base_uri}date")

}
