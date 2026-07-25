
import dev.tesserakt.rdf.dsl.buildStore
import dev.tesserakt.rdf.ontology.RDF
import dev.tesserakt.rdf.serialization.DelicateSerializationApi
import dev.tesserakt.rdf.serialization.common.deserialize
import dev.tesserakt.rdf.serialization.common.serializer
import dev.tesserakt.rdf.serialization.turtle.Turtle
import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.rdf.types.Quad.NamedTerm
import dev.tesserakt.rdf.types.toStore
import dev.tesserakt.sparql.Query
import dev.tesserakt.sparql.debug.BindingsTable.Companion.tabulate
import dev.tesserakt.sparql.query
import kotlin.test.Test
import kotlin.test.assertFailsWith

class QueryStructureStateTest {

    private fun buildAddressesStore() = buildStore {
        NamedTerm("person1") has NamedTerm("domicile") being blank {
            NamedTerm("address") being blank {
                NamedTerm("street") being Quad.Literal("Person St.")
                NamedTerm("city") being blank {
                    NamedTerm("inhabitants") being 5000
                }
            }
        }
        NamedTerm("person2") has NamedTerm("domicile") being NamedTerm("house2")
        NamedTerm("house2") has NamedTerm("address") being NamedTerm("address2")
        NamedTerm("address2") has NamedTerm("street") being Quad.Literal("Person II St.")
        NamedTerm("address2") has NamedTerm("city") being blank {
            NamedTerm("inhabitants") being 7500
        }
        NamedTerm("incomplete") has NamedTerm("domicile") being blank {
            NamedTerm("address") being blank {
                NamedTerm("street") being NamedTerm("unknown")
                NamedTerm("city") being NamedTerm("unknown")
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
            person has RDF.type being NamedTerm("person")
            person has NamedTerm("age") being 23
            person has NamedTerm("notes") being list(
                NamedTerm("first-note"),
                NamedTerm("second-note"),
                NamedTerm("third-note"),
                NamedTerm("fourth-note"),
                NamedTerm("another-note"),
                NamedTerm("last-note"),
            )
            person has NamedTerm("notes") being list(
                NamedTerm("even-more-notes")
            )
            person has NamedTerm("decoy") being list(
                NamedTerm("wrong-1"),
                NamedTerm("wrong-2"),
                NamedTerm("wrong-3"),
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
    fun subquery() {
        with(VerboseCompiler) {
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
            assertFailsWith(NotImplementedError::class) {
                println("Found alt path:\n${store.query(union).tabulate()}")
            }
        }
    }

    @OptIn(DelicateSerializationApi::class)
    @Test
    fun aggregation() = with(VerboseCompiler) {
        // src: https://www.w3.org/TR/sparql11-query/#aggregateExample
        val store = serializer(Turtle).deserialize("""
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
        """).toStore()
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

    @OptIn(DelicateSerializationApi::class)
    @Test
    fun filters() = with (VerboseCompiler) {
        val data1 = serializer(Turtle).deserialize("""
            @prefix  :       <http://example/> .
            @prefix  rdf:    <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
            @prefix  foaf:   <http://xmlns.com/foaf/0.1/> .

            :alice  rdf:type   foaf:Person .
            :alice  foaf:name  "Alice" .
            :bob    rdf:type   foaf:Person .
        """).toStore()
        val query1 = """
            PREFIX  rdf:    <http://www.w3.org/1999/02/22-rdf-syntax-ns#> 
            PREFIX  foaf:   <http://xmlns.com/foaf/0.1/> 

            SELECT ?person
            WHERE 
            {
                ?person rdf:type  foaf:Person .
                FILTER NOT EXISTS { ?person foaf:name ?name }
            } 
        """.toSparqlSelectQuery()
        println("Results:\n${data1.query(query1).tabulate()}")
    }

}
