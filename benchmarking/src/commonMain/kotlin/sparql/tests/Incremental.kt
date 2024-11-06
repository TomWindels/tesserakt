package sparql.tests

import dev.tesserakt.rdf.dsl.RdfContext.Companion.buildStore
import dev.tesserakt.rdf.ontology.RDF
import dev.tesserakt.rdf.types.Quad.Companion.asLiteralTerm
import dev.tesserakt.rdf.types.Quad.Companion.asNamedTerm
import dev.tesserakt.testing.testEnv
import sparql.types.using


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
            ?s (<http://example.org/path1>|<http://example.org/path2>) ?o
        }
    """

    using(small) test """
        SELECT * {
            ?s (<http://example.org/path1>/!<http://example.org/path2>) ?o
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

    using(medium) test """
        PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
        SELECT ?person ?note ?decoy {
            ?person a <http://example.org/person> .
            {
                ?person <http://example.org/notes>/rdf:rest*/rdf:first ?note
            } UNION {
                ?person <http://example.org/decoy>/rdf:rest*/rdf:first ?decoy
            }
        }
    """

    val chain = buildStore {
        val start = local("start")
        val end = local("end")
        val path = "http://example.org/path".asNamedTerm()
        start has path being end
        start has path being blank {
            path being end
            path being blank {
                path being end
                path being blank {
                    path being end
                }
            }
        }
    }

    using(chain) test """
        SELECT ?s ?e {
            ?s (<http://example.org/path>/<http://example.org/path>)* ?e
        }
    """

    using(chain) test """
        SELECT * {
            # This line is apparently not valid SPARQL; oh well
            # ?s (<http://example.org/path>*)/?p ?e
            ?s <http://example.org/path>* ?b .
            ?b ?p ?e
        }
    """

    val person = buildStore {
        val person1 = "http://example.org/person1".asNamedTerm()
        val person2 = "http://example.org/person2".asNamedTerm()
        person1 has "http://example.org/givenName".asNamedTerm() being "John".asLiteralTerm()
        person1 has "http://example.org/surname".asNamedTerm() being "Doe".asLiteralTerm()
        person2 has "http://example.org/givenName".asNamedTerm() being "A".asLiteralTerm()
        person2 has "http://example.org/surname".asNamedTerm() being "B".asLiteralTerm()
    }

    using(person) test """
        PREFIX : <http://example.org/>
        SELECT ?name {
            ?person :givenName ?gName ; :surname ?sName .
            BIND(CONCAT(?gName, " ", ?sName) AS ?name)
            FILTER(STRLEN(?name) > 3)
        }
    """

    val fullyConnected = buildStore {
        val a = "http://example.org/a".asNamedTerm()
        val b = "http://example.org/b".asNamedTerm()
        val c = "http://example.org/c".asNamedTerm()
        val p = "http://example.org/p".asNamedTerm()
        a has p being b
        a has p being c
        b has p being a
        b has p being c
        c has p being a
        c has p being b
    }

    using(fullyConnected) test """
        SELECT * WHERE {
            <http://example.org/a> <http://example.org/p>* <http://example.org/b>
        }
    """

    using(fullyConnected) test """
        SELECT * WHERE {
            ?a <http://example.org/p>* <http://example.org/b>
        }
    """

    using(fullyConnected) test """
        SELECT * WHERE {
            ?a <http://example.org/p>* ?b
        }
    """

    val unions = buildStore("http://www.example.org/") {
        val a = local("a")
        val b = local("b")
        val c = local("c")
        val d = local("d")
        val e = local("e")

        val p1 = local("p1")
        val p2 = local("p2")
        val p3 = local("p3")
        val p4 = local("p4")

        a has p1 being b
        b has p4 being c
        a has p2 being d
        d has p3 being c
        a has p1 being e
    }

    using(unions) test """
        PREFIX : <http://www.example.org/>
        SELECT ?s WHERE {
            {
                :a :p1 ?b .
            } UNION {
                :a :p2 ?b .
            }
            {
                ?b :p3 ?s .
            } UNION {
                ?b :p4 ?s .
            }
        }
    """

    using(unions) test """
        PREFIX : <http://www.example.org/>
        SELECT ?s WHERE {
            :a (:p1|:p2)/(:p3|:p4) ?s
        }
    """

}
