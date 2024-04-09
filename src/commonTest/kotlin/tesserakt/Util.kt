package tesserakt

import tesserakt.rdf.dsl.RdfContext
import tesserakt.rdf.nt
import tesserakt.rdf.ontology.Ontology
import tesserakt.rdf.ontology.RDF
import tesserakt.rdf.types.Store
import tesserakt.rdf.types.Triple.Companion.asLiteral

object FOAF: Ontology {

    override val prefix = "foaf"
    override val base_uri = "http://xmlns.com/foaf/0.1/"

    val Person = "${base_uri}Person".nt
    val age = "${base_uri}age".nt
    val knows = "${base_uri}knows".nt
    val based_near = "${base_uri}based_near".nt

}


fun createTestStore(): Store = RdfContext.buildStore {
    val person = local("person1")
    person has RDF.type being FOAF.Person
    person has FOAF.age being 23
    person has FOAF.knows being multiple(
        local("person2"), local("person3"), local("person4")
    )
    person has FOAF.based_near being blank {
        "street".nt being "unknown".asLiteral()
        "number".nt being (-1).asLiteral()
    }
    person has "notes".nt being list(
        "first-note".nt, "second-note".nt
    )
}
