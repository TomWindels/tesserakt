
import dev.tesserakt.rdf.dsl.buildStore
import dev.tesserakt.rdf.ontology.Ontology
import dev.tesserakt.rdf.ontology.RDF
import dev.tesserakt.rdf.types.Quad.Companion.asLiteralTerm
import dev.tesserakt.rdf.types.Quad.Companion.asNamedTerm
import dev.tesserakt.rdf.types.Store

object FOAF: Ontology {

    override val prefix = "foaf"
    override val base_uri = "http://xmlns.com/foaf/0.1/"

    val Person = "${base_uri}Person".asNamedTerm()
    val age = "${base_uri}age".asNamedTerm()
    val knows = "${base_uri}knows".asNamedTerm()
    val based_near = "${base_uri}based_near".asNamedTerm()

}


fun createTestStore(): Store = buildStore {
    val person = local("person1")
    person has RDF.type being FOAF.Person
    person has FOAF.age being 23
    person has FOAF.knows being multiple(
        local("person2"), local("person3"), local("person4")
    )
    person has FOAF.based_near being blank {
        "street".asNamedTerm() being "unknown".asLiteralTerm()
        "number".asNamedTerm() being (-1).asLiteralTerm()
    }
    person has "notes".asNamedTerm() being list(
        "first-note".asNamedTerm(), "second-note".asNamedTerm()
    )
}
