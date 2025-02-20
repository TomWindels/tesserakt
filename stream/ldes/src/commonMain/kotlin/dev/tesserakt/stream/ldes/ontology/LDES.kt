package dev.tesserakt.stream.ldes.ontology

import dev.tesserakt.rdf.ontology.Ontology

object LDES: Ontology {

    override val prefix = "ldes"
    override val base_uri = "https://w3id.org/ldes#"

    val EventStream = this("EventStream")
    val timestampPath = this("timestampPath")
    val versionOfPath = this("versionOfPath")
    val member get() = TREE.member

}
