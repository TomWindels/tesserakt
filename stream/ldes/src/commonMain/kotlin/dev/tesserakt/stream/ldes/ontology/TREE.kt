package dev.tesserakt.stream.ldes.ontology

import dev.tesserakt.rdf.ontology.Ontology

object TREE: Ontology {

    override val prefix = "tree"
    override val base_uri = "https://w3id.org/tree#"

    val member = this("member")

}
