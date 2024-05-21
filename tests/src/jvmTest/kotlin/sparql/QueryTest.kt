package sparql

import createTestStore
import dev.tesserakt.rdf.dsl.RdfContext.Companion.buildStore
import dev.tesserakt.rdf.lt
import dev.tesserakt.rdf.nt
import dev.tesserakt.rdf.ontology.RDF
import dev.tesserakt.rdf.types.Quad.Companion.asNamedTerm
import dev.tesserakt.sparql.BindingsTable.Companion.tabulate
import dev.tesserakt.sparql.runtime.query.Query.Companion.query
import dev.tesserakt.util.console.toStylisedString
import kotlin.test.Test

class QueryTest {

    private fun buildAddressesStore() = buildStore {
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

    @Test
    fun simple() = with (VerboseCompiler) {
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
    fun medium() = with (VerboseCompiler) {
        val store = createTestStore()

        val random = "SELECT ?data { ?s a|<age>|<friend> ?data }".asSPARQLSelectQuery()
        println("Found `random` bindings:\n${store.query(random).tabulate()}")

        val address = "SELECT ?street { ?s (a|<address>)/<street> ?street }".asSPARQLSelectQuery()
        store.query(address) {
            println("Found `address` binding:\n$it")
        }

        val any = "SELECT ?s ?o { ?s (<>|!<>) ?o }".asSPARQLSelectQuery()
        val result = store.query(any)
        println("Found ${result.size} elements for the `any` query, expected ${store.size}")

        val info = "SELECT ?s ?o { ?s !(<friend>|<notes>|<address>) ?o }".asSPARQLSelectQuery()
        println("Found `info` data:\n${store.query(info).tabulate()}")
    }

    @Test
    fun advanced() = with (VerboseCompiler) {
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
    fun blank() = with (VerboseCompiler) {
        val store = buildAddressesStore()

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

    @Test
    fun optional() = with (VerboseCompiler) {
        val store = buildAddressesStore()

        val optional = """
            SELECT * {
                ?person <domicile>/<address> ?place .
                ?place <street> ?street .
                OPTIONAL {
                    ?place <city>/<inhabitants> ?count .
                }
            }
        """.asSPARQLSelectQuery()
        println("Found optional:\n${store.query(optional).tabulate()}")
    }

    @Test
    fun union() = with(VerboseCompiler) {
        val store = buildAddressesStore()

        val union = """
            SELECT * {
                ?person <domicile>/<address> ?place .
                ?place <street> ?street .
                {
                    ?place <city> ?city .
                     OPTIONAL {
                        ?city <inhabitants> ?count .
                     }
                } UNION {
                    ?place <city> <unknown> .
                }
            }
        """.asSPARQLSelectQuery()
        println("Found union:\n${store.query(union).tabulate()}")
    }

    @Test
    fun altPath() = with(VerboseCompiler) {
        val store = buildAddressesStore()

        val alt = """
            SELECT * {
                ?a (<domicile>/<address>)|(<city>/<inhabitants>) ?b .
            }
        """.asSPARQLSelectQuery()
        println("Found alt path:\n${store.query(alt).tabulate()}")
    }

    @Test
    fun subquery() = with(VerboseCompiler) {
        val store = buildAddressesStore()

        val union = """
            SELECT * {
                ?person <domicile>/<address> ?place .
                ?place <street> ?street .
                {
                    SELECT * { ?s ?p ?o }
                }
            }
        """.asSPARQLSelectQuery()
        println("Found alt path:\n${store.query(union).tabulate()}")
    }

}
