package core.rdf.ontology

import core.rdf.types.Triple.Companion.asNamedNode

object RDF: Ontology {

    override val prefix = "rdf"
    override val base_uri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#"

    val type = "${base_uri}type".asNamedNode()
    val first = "${base_uri}first".asNamedNode()
    val rest = "${base_uri}rest".asNamedNode()
    val nil = "${base_uri}nil".asNamedNode()

}
