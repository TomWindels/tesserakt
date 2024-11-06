import dev.tesserakt.rdf.dsl.RdfContext.Companion.buildStore
import dev.tesserakt.rdf.literalTerm
import dev.tesserakt.rdf.namedTerm
import dev.tesserakt.rdf.ontology.RDF
import dev.tesserakt.rdf.serialization.Turtle.parseTurtleString
import dev.tesserakt.rdf.types.Quad.Companion.asNamedTerm
import dev.tesserakt.sparql.BindingsTable.Companion.tabulate
import dev.tesserakt.sparql.runtime.incremental.query.IncrementalQuery.Companion.query
import kotlin.test.Test

class IncrementalQueryTest {

    private fun buildAddressesStore() = buildStore {
        "person1".namedTerm has "domicile".namedTerm being blank {
            "address".namedTerm being blank {
                "street".namedTerm being "Person St.".literalTerm
                "city".namedTerm being blank {
                    "inhabitants".namedTerm being 5000
                }
            }
        }
        "person2".namedTerm has "domicile".namedTerm being "house2".namedTerm
        "house2".namedTerm has "address".namedTerm being "address2".namedTerm
        "address2".namedTerm has "street".namedTerm being "Person II St.".literalTerm
        "address2".namedTerm has "city".namedTerm being blank {
            "inhabitants".namedTerm being 7500
        }
        "incomplete".namedTerm has "domicile".namedTerm being blank {
            "address".namedTerm being blank {
                "street".namedTerm being "unknown".namedTerm
                "city".namedTerm being "unknown".namedTerm
            }
        }
    }

    @Test
    fun simple() = with (VerboseCompiler) {
        val store = createTestStore()

        val simple = "SELECT * WHERE { ?s ?p ?o }".asSPARQLSelectQuery()
        val spo = store.query(simple)
        println("Found ${spo.size} bindings for the spo-query. Expected ${store.size}")

        val chain = "SELECT * WHERE { ?person <${FOAF.based_near}>/<number> ?number ; <${FOAF.based_near}>/<street> ?street }".asSPARQLSelectQuery()
        store.query(chain) {
            println("Found `chain` binding\n$it")
        }

        val multiple = "SELECT ?friend WHERE { ?person <${FOAF.knows}> ?friend ; a <${FOAF.Person}> }".asSPARQLSelectQuery()
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
        """.asSPARQLSelectQuery()
        println("Results:\n${store.query(query).tabulate()}")
    }

}
