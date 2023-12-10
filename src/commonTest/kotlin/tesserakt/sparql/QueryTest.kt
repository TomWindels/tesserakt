package tesserakt.sparql

import tesserakt.createTestStore
import tesserakt.sparql.SPARQL.asSPARQLSelectQuery
import tesserakt.sparql.runtime.query.Query.Companion.query
import tesserakt.sparql.runtime.query.Query.Companion.queryAsList
import kotlin.test.Test

class QueryTest {

    @Test
    fun simple() {
        val store = createTestStore()

        val simple = "SELECT * WHERE { ?s ?p ?o }".asSPARQLSelectQuery()
        val spo = store.queryAsList(simple)
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

}