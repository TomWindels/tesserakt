package dev.tesserakt.stream.ldes.ontology

import dev.tesserakt.rdf.ontology.Ontology

object DC: Ontology {

    override val prefix = "dc"
    override val base_uri = "http://purl.org/dc/elements/1.1/"

    val modified = this("modified")
    val isVersionOf = this("isVersionOf")

}
