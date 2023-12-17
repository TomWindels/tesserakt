package tesserakt.sparql

import tesserakt.createTestStore
import tesserakt.rdf.dsl.RdfContext.Companion.buildStore
import tesserakt.rdf.lt
import tesserakt.rdf.nt
import tesserakt.rdf.ontology.RDF
import tesserakt.rdf.types.Triple.Companion.asNamedTerm
import tesserakt.sparql.Compiler.Default.asSPARQLSelectQuery
import tesserakt.sparql.runtime.query.Query.Companion.query
import tesserakt.util.BindingsTable.Companion.tabulate
import tesserakt.util.console.toStylisedString
import kotlin.test.Test

class QueryTest {

    @Test
    fun simple() {
        val store = createTestStore()

        val simple = "SELECT * WHERE { ?s ?p ?o }".asSPARQLSelectQuery()
        val spo = store.query(simple)
        println("Found ${spo.size} bindings for the spo-query. Expected ${store.size}")

        val chain = "SELECT * WHERE { ?person <address>/<number> ?number ; <address>/<street> ?street }".asSPARQLSelectQuery()
        store.query(chain) {
            println("Found `chain` binding\n$it")
        }

        val multiple = "SELECT ?friend WHERE { ?person <friend> ?friend ; a <person> }".asSPARQLSelectQuery()
        store.query(multiple) {
            println("Found `multiple` binding\n$it")
        }
    }

    @Test
    fun medium() {
        val store = createTestStore()

        val random = "SELECT ?data { ?s a|<age>|<friend> ?data }".asSPARQLSelectQuery()
        store.query(random) {
            println("Found `random` binding:\n$it")
        }

        val address = "SELECT ?street { ?s (a|<address>)/<street> ?street }".asSPARQLSelectQuery()
        store.query(address) {
            println("Found `address` binding:\n$it")
        }

        val any = "SELECT ?s ?o { ?s (<>|!<>) ?o }".asSPARQLSelectQuery()
        val result = store.query(any)
        println("Found ${result.size} elements for the `any` query, expected ${store.size}")

        val info = "SELECT ?s ?o { ?s !(<friend>|<notes>|<address>) ?o }".asSPARQLSelectQuery()
        store.query(info) {
            println("Found `info` binding:\n$it")
        }
    }

    @Test
    fun advanced() {
        val store = buildStore {
            val person = local("person1")
            person has RDF.type being "person".asNamedTerm()
            person has "age".asNamedTerm() being 23
            person has "notes".asNamedTerm() being list(
                "first-note".asNamedTerm(),
                "second-note".asNamedTerm(),
                "third-note".asNamedTerm(),
                "fourth-note".asNamedTerm(),
                "another-note".asNamedTerm(),
                "last-note".asNamedTerm(),
            )
            person has "notes".asNamedTerm() being list(
                "even-more-notes".asNamedTerm()
            )
            person has "decoy".asNamedTerm() being list(
                "wrong-1".asNamedTerm(),
                "wrong-2".asNamedTerm(),
                "wrong-3".asNamedTerm(),
            )
        }

        val nodes = """
            PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
            SELECT ?node {
                ?node rdf:rest* ?blank .
                ?blank rdf:rest rdf:nil .
            }
        """.asSPARQLSelectQuery()
        // expected result: blank1, blank2, blank3, blank...
        println("Found blank nodes:\n${store.query(nodes).tabulate()}")

        val list = """
            PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
            SELECT ?person ?note {
                ?person a <person> ; <notes>/rdf:rest*/rdf:first ?note
            }
        """.asSPARQLSelectQuery()
        // expected: [person, first-note], [person, second-note] ...
        println("Found list entries:\n${store.query(list).tabulate()}")

        val any = """
            PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
            SELECT * {
                ?s (<>|!<>)* ?o
            }
        """.asSPARQLSelectQuery()
        // expecting a lot of results
        println("Found \"any\" entries:\n${store.query(any).tabulate()}")

        val traversal = """
            PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
            SELECT ?person ?p ?result {
                ?person <notes>/?p+ ?result .
            }
        """.asSPARQLSelectQuery()
        println("Found \"traversal\" entries:\n${store.query(traversal).tabulate().apply { order("person", "p", "result")}.toStylisedString() }")
    }

    @Test
    fun blank() {
        val store = buildStore {
            "person1".nt has "domicile".nt being blank {
                "address".nt being blank {
                    "street".nt being "Person St.".lt
                    "city".nt being blank {
                        "inhabitants".nt being 5000
                    }
                }
            }
            "person2".nt has "domicile".nt being "house2".nt
            "house2".nt has "address".nt being "address2".nt
            "address2".nt has "street".nt being "Person II St.".lt
            "address2".nt has "city".nt being blank {
                "inhabitants".nt being 7500
            }
            "incomplete".nt has "domicile".nt being blank {
                "address".nt being blank {
                    "street".nt being "unknown".nt
                    "city".nt being "unknown".nt
                }
            }
        }

        val blank = """
            SELECT * {
                ?person <domicile> [
                    <address> [
                        <street> ?street ;
                        <city> [
                            <inhabitants> ?count
                        ]
                    ]
                ] .
            }
        """.asSPARQLSelectQuery()
        println("Found address:\n${store.query(blank).tabulate()}")
    }

}
