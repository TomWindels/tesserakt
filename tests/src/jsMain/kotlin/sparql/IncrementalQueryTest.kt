package sparql

import dev.tesserakt.rdf.dsl.RdfContext.Companion.buildStore
import dev.tesserakt.rdf.ontology.RDF
import dev.tesserakt.rdf.types.Quad.Companion.asNamedTerm
import sparql.util.TestEnvironment.Companion.test

fun compareIncrementalBasicGraphPatternOutput() = test {
    val store = buildStore {
        val person = local("person1")
        person has RDF.type being "http://example.org/person".asNamedTerm()
        person has "http://example.org/age".asNamedTerm() being 23
        person has "http://example.org/notes".asNamedTerm() being list(
            "http://example.org/first-note".asNamedTerm(),
            "http://example.org/second-note".asNamedTerm(),
            "http://example.org/third-note".asNamedTerm(),
            "http://example.org/fourth-note".asNamedTerm(),
            "http://example.org/another-note".asNamedTerm(),
            "http://example.org/last-note".asNamedTerm(),
        )
        person has "http://example.org/notes".asNamedTerm() being list(
            "http://example.org/even-more-notes".asNamedTerm()
        )
        person has "http://example.org/decoy".asNamedTerm() being list(
            "http://example.org/wrong-1".asNamedTerm(),
            "http://example.org/wrong-2".asNamedTerm(),
            "http://example.org/wrong-3".asNamedTerm(),
        )
    }

    using(store) test """
        PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
        SELECT ?node {
            ?node rdf:rest* ?blank .
            ?blank rdf:rest rdf:nil .
        }
    """

    using(store) test """
        PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
        SELECT ?node {
            ?node rdf:rest* ?blank .
            ?blank rdf:rest rdf:nil .
        }
    """

    using(store) test """
        PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
        SELECT ?person ?note {
            ?person a <http://example.org/person> ; <http://example.org/notes>/rdf:rest*/rdf:first ?note
        }
    """

    using(store) test """
        PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
        SELECT * {
            ?s (<http://example.org/>|!<http://example.org/>)* ?o
        }
    """
}
