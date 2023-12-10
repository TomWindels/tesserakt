package tesserakt

import tesserakt.rdf.dsl.RdfContext
import tesserakt.rdf.ontology.RDF
import tesserakt.rdf.types.Store
import tesserakt.rdf.types.Triple.Companion.asLiteral
import tesserakt.rdf.types.Triple.Companion.asNamedTerm

fun createTestStore(): Store = RdfContext.buildStore {
    val person = local("person1")
    person has RDF.type being "person".asNamedTerm()
    person has "age".asNamedTerm() being 23
    person has "friend".asNamedTerm() being multiple(
        local("person2"), local("person3"), local("person4")
    )
    person has "address".asNamedTerm() being blank {
        "street".asNamedTerm() being "unknown".asLiteral()
        "number".asNamedTerm() being (-1).asLiteral()
    }
    person has "notes".asNamedTerm() being list(
        "first-note".asNamedTerm(), "second-note".asNamedTerm()
    )
}
