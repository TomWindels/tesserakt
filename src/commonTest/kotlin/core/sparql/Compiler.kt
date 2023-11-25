package core.sparql

import core.rdf.SPARQL
import kotlin.test.Test

class Compiler {

    companion object {

        private const val VALID_1 = "SELECT ?s ?p ?o WHERE { ?s ?p ?o . }"
        private const val VALID_2 = """
            PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
            SELECT * WHERE {
               ?s<predicate1>   /<predicate2>*/ <predicate3> ?o .
            }"""
        private const val VALID_3 = """
            # PREFIX dc:<http://purl.org/dc/elements/1.1/>
            PREFIX ex: <http://example.com/rdf-schema#> # ah yes mah ontology
            SELECT * WHERE { # replace TEST with * again !
                ?s a ?type ;
                   <contains> ?o2 .
            }"""
        private const val VALID_4 = "CONSTRUCT { <test> <containsObject> ?o . } WHERE { ?s ?p ?o . }"

        private const val INVALID_1 = "SELECT TEST WHERE { ?s a TEST . }"
        private const val INVALID_2 = """
            PREFIX ex: <http://example.com/rdf-schema # > # bad use of spaces
            SELECT UNION WHERE { # 'UNION' is an incorrect keyword
                ?s ex:predicate1 ?o1 ;
                   <predicate2> ?o2 .
            }"""

        val valid = listOf(
            VALID_1,
            VALID_2,
            VALID_3,
            VALID_4,
        )

        val invalid = listOf(
            INVALID_1,
            INVALID_2
        )

    }

    @Test
    fun compileQuery() {
        valid.forEachIndexed { i, query ->
            try {
                println("Compiling valid #${i + 1}")
                SPARQL.parse(query)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        invalid.forEachIndexed { i, query ->
            try {
                println("Compiling invalid #${i + 1}")
                SPARQL.parse(query)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

}