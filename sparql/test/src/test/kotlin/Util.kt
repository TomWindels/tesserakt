
import dev.tesserakt.rdf.dsl.buildStore
import dev.tesserakt.rdf.ontology.Ontology
import dev.tesserakt.rdf.ontology.RDF
import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.rdf.types.Quad.NamedTerm
import dev.tesserakt.rdf.types.Store

object FOAF: Ontology {

    override val prefix = "foaf"
    override val base_uri = "http://xmlns.com/foaf/0.1/"

    val Person = NamedTerm("${base_uri}Person")
    val age = NamedTerm("${base_uri}age")
    val knows = NamedTerm("${base_uri}knows")
    val based_near = NamedTerm("${base_uri}based_near")

}


fun createTestStore(): Store = buildStore {
    val person = local("person1")
    person has RDF.type being FOAF.Person
    person has FOAF.age being 23
    person has FOAF.knows being multiple(
        local("person2"), local("person3"), local("person4")
    )
    person has FOAF.based_near being blank {
        NamedTerm("street") being Quad.Literal("unknown")
        NamedTerm("number") being Quad.Literal((-1))
    }
    person has NamedTerm("notes") being list(
        NamedTerm("first-note"), NamedTerm("second-note")
    )
}
