package sparql.tests

import dev.tesserakt.rdf.dsl.RdfContext.Companion.buildStore
import dev.tesserakt.rdf.ontology.RDF
import dev.tesserakt.rdf.types.Quad.Companion.asNamedTerm
import test.suite.testEnv


fun compareIncrementalBasicGraphPatternOutput() = testEnv {
    val small = buildStore {
        val subj = local("s")
        val obj = local("o")
        val intermediate = local("i")
        val path1 = "http://example.org/path1".asNamedTerm()
        val path2 = "http://example.org/path2".asNamedTerm()
        subj has path1 being obj
        subj has path2 being obj
        subj has path1 being intermediate
        intermediate has path1 being obj
    }

    using(small) test """
        SELECT * {
            # ?s (<http://example.org/path1>|<http://example.org/path2>) ?o
            ?s (<path1>|<path2>) ?o
        }
    """

    using(small) test """
        SELECT * {
            ?s !<http://example.org/path3> ?o
        }
    """

    using(small) test """
        SELECT * {
            ?s (<http://example.org/path1>|<http://example.org/path2>)* ?o
        }
    """

    val medium = buildStore {
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

    using(medium) test """
        PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
        SELECT ?node {
            ?node rdf:rest* ?blank .
            ?blank rdf:rest rdf:nil .
        }
    """

    using(medium) test """
        PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
        SELECT ?person ?note {
            ?person a <http://example.org/person> ; <http://example.org/notes>/rdf:rest*/rdf:first ?note
        }
    """

    using(medium) test """
        PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
        SELECT * {
            ?s (<http://example.org/>|!<http://example.org/>)* ?o
        }
    """
}
