
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
    (person) (RDF.type) (FOAF.Person)
    (person) (FOAF.age) (23)
    (person) (FOAF.knows) (
        local("person2"), local("person3"), local("person4")
    )
    (person) (FOAF.based_near) blank {
        (NamedTerm("street")) (Quad.Literal("unknown"))
        (NamedTerm("number")) (Quad.Literal(-1))
    }
    (person) (NamedTerm("notes")) list listOf(
        NamedTerm("first-note"), NamedTerm("second-note")
    )
}
