import dev.tesserakt.rdf.dsl.RdfContext
import dev.tesserakt.rdf.namedTerm
import dev.tesserakt.rdf.ontology.Ontology
import dev.tesserakt.rdf.ontology.RDF
import dev.tesserakt.rdf.types.Store
import dev.tesserakt.rdf.types.Quad.Companion.asLiteral

object FOAF: Ontology {

    override val prefix = "foaf"
    override val base_uri = "http://xmlns.com/foaf/0.1/"

    val Person = "${base_uri}Person".namedTerm
    val age = "${base_uri}age".namedTerm
    val knows = "${base_uri}knows".namedTerm
    val based_near = "${base_uri}based_near".namedTerm

}


fun createTestStore(): Store = RdfContext.buildStore {
    val person = local("person1")
    person has RDF.type being FOAF.Person
    person has FOAF.age being 23
    person has FOAF.knows being multiple(
        local("person2"), local("person3"), local("person4")
    )
    person has FOAF.based_near being blank {
        "street".namedTerm being "unknown".asLiteral()
        "number".namedTerm being (-1).asLiteral()
    }
    person has "notes".namedTerm being list(
        "first-note".namedTerm, "second-note".namedTerm
    )
}
