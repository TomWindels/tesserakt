
import dev.tesserakt.rdf.dsl.buildStore
import dev.tesserakt.rdf.ontology.RDF
import dev.tesserakt.rdf.serialization.Turtle.parseTurtleString
import dev.tesserakt.rdf.types.Quad.Companion.asLiteralTerm
import dev.tesserakt.rdf.types.Quad.Companion.asNamedTerm
import dev.tesserakt.sparql.Query
import dev.tesserakt.sparql.query
import dev.tesserakt.util.console.BindingsTable.Companion.tabulate
import kotlin.test.Test

class QueryStructureStateTest {

    private fun buildAddressesStore() = buildStore {
        "person1".asNamedTerm() has "domicile".asNamedTerm() being blank {
            "address".asNamedTerm() being blank {
                "street".asNamedTerm() being "Person St.".asLiteralTerm()
                "city".asNamedTerm() being blank {
                    "inhabitants".asNamedTerm() being 5000
                }
            }
        }
        "person2".asNamedTerm() has "domicile".asNamedTerm() being "house2".asNamedTerm()
        "house2".asNamedTerm() has "address".asNamedTerm() being "address2".asNamedTerm()
        "address2".asNamedTerm() has "street".asNamedTerm() being "Person II St.".asLiteralTerm()
        "address2".asNamedTerm() has "city".asNamedTerm() being blank {
            "inhabitants".asNamedTerm() being 7500
        }
        "incomplete".asNamedTerm() has "domicile".asNamedTerm() being blank {
            "address".asNamedTerm() being blank {
                "street".asNamedTerm() being "unknown".asNamedTerm()
                "city".asNamedTerm() being "unknown".asNamedTerm()
            }
        }
    }

    @Test
    fun simple() = with (VerboseCompiler) {
        val store = createTestStore()

        val simple = "SELECT * WHERE { ?s ?p ?o }".toSparqlSelectQuery()
        val spo = store.query(simple)
        println("Found ${spo.size} bindings for the spo-query. Expected ${store.size}")

        val chain = "SELECT * WHERE { ?person <${FOAF.based_near}>/<number> ?number ; <${FOAF.based_near}>/<street> ?street }".toSparqlSelectQuery()
        store.query(chain) {
            println("Found `chain` binding\n$it")
        }

        val multiple = "SELECT ?friend WHERE { ?person <${FOAF.knows}> ?friend ; a <${FOAF.Person}> }".toSparqlSelectQuery()
        store.query(multiple) {
            println("Found `multiple` binding\n$it")
        }
    }

    @Test
    fun medium() = with (VerboseCompiler) {
        val store = createTestStore()

        val random = "SELECT ?data { ?s a|<age>|<friend> ?data }".toSparqlSelectQuery()
        println("Found `random` bindings:\n${store.query(random).tabulate()}")

        val address = Query.Select("SELECT ?street { ?s (a|<address>)/<street> ?street }")
        store.query(address) {
            println("Found `address` binding:\n$it")
        }

        val any = "SELECT ?s ?o { ?s (<>|!<>) ?o }".toSparqlSelectQuery()
        val result = store.query(any)
        println("Found ${result.size} elements for the `any` query, expected ${store.size}")

        val info = "SELECT ?s ?o { ?s !(<friend>|<notes>|<address>) ?o }".toSparqlSelectQuery()
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
        """.toSparqlSelectQuery()
        // expected result: blank1, blank2, blank3, blank...
        println("Found blank nodes:\n${store.query(nodes).tabulate()}")

        val list = """
            PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
            SELECT ?person ?note {
                ?person a <person> ; <notes>/rdf:rest*/rdf:first ?note
            }
        """.toSparqlSelectQuery()
        // expected: [person, first-note], [person, second-note] ...
        println("Found list entries:\n${store.query(list).tabulate()}")

        val any = """
            PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
            SELECT * {
                ?s (<>|!<>)* ?o
            }
        """.toSparqlSelectQuery()
        // expecting a lot of results
        println("Found \"any\" entries:\n${store.query(any).tabulate()}")
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
        """.toSparqlSelectQuery()
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
        """.toSparqlSelectQuery()
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
        """.toSparqlSelectQuery()
        println("Found union:\n${store.query(union).tabulate()}")
    }

    @Test
    fun altPath() = with(VerboseCompiler) {
        val store = buildAddressesStore()

        val alt = """
            SELECT * {
                ?a (<domicile>/<address>)|(<city>/<inhabitants>) ?b .
            }
        """.toSparqlSelectQuery()
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
        """.toSparqlSelectQuery()
        println("Found alt path:\n${store.query(union).tabulate()}")
    }

    @Test
    fun aggregation() = with(VerboseCompiler) {
        // src: https://www.w3.org/TR/sparql11-query/#aggregateExample
        val store = """
            @prefix : <http://books.example/> .

            :org1 :affiliates :auth1, :auth2 .
            :auth1 :writesBook :book1, :book2 .
            :book1 :price 9 .
            :book2 :price 5 .
            :auth2 :writesBook :book3 .
            :book3 :price 7 .
            :org2 :affiliates :auth3 .
            :auth3 :writesBook :book4 .
            :book4 :price 7 .
        """.parseTurtleString()
        val query = """
            PREFIX : <http://books.example/>
            SELECT (SUM(?lprice) AS ?totalPrice)
            WHERE {
              ?org :affiliates ?auth .
              ?auth :writesBook ?book .
              ?book :price ?lprice .
            }
            GROUP BY ?org
            HAVING (SUM(?lprice) > 10)
        """.toSparqlSelectQuery()
        println("Results:\n${store.query(query).tabulate()}")
    }

}
