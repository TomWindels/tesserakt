package tesserakt.sparql

import tesserakt.rdf.SPARQL
import tesserakt.rdf.types.Triple
import tesserakt.sparql.CompilerTest.Environment.Companion.test
import tesserakt.sparql.compiler.CompilerError
import tesserakt.sparql.compiler.types.Pattern
import tesserakt.sparql.compiler.types.QueryAST
import tesserakt.sparql.compiler.types.SelectQueryAST
import tesserakt.util.printerrln
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CompilerTest {

    class Environment private constructor() {

        companion object {

            fun test(block: Environment.() -> Unit) {
                Environment().apply(block).test()
            }

        }

        sealed class Test {

            abstract val input: String

            abstract operator fun invoke()

        }

        data class ASTTest <Q: QueryAST> (
            override val input: String,
            val test: Q.() -> Boolean
        ): Test() {

            override operator fun invoke() {
                @Suppress("UNCHECKED_CAST")
                val ast = SPARQL.process(input) as Q
                assertTrue(test(ast), "Validation did not succeed! Got AST $ast")
            }

        }

        data class CompilationFailureTest(
            override val input: String,
            val type: CompilerError.Type
        ): Test() {

            override operator fun invoke() {
                try {
                    SPARQL.process(input)
                    throw AssertionError("Compilation succeeded unexpectedly!")
                } catch (c: CompilerError) {
                    // exactly what is expected, so not throwing anything
                    assertEquals(c.type, type, "Compilation failed for a different reason!")
                }
                // all other exceptions are still being thrown
            }

        }

        private val tests = mutableListOf<Test>()

        fun <Q: QueryAST> String.satisfies(validation: Q.() -> Boolean) {
            tests.add(ASTTest(input = this, test = validation))
        }

        fun String.shouldFail(error: CompilerError.Type) {
            tests.add(CompilationFailureTest(input = this, type = error))
        }

        private fun test() {
            val failures = mutableListOf<Pair<Int, Throwable>>()
            tests.forEachIndexed { i, test ->
                try {
                    test()
                } catch (t: Throwable) {
                    failures.add(i to t)
                }
            }
            failures.forEach { (i, t) ->
                printerrln("Query ${i + 1} failed: `${tests[i].input}`")
                if (t is CompilerError) {
                    printerrln(t.stacktrace)
                } else {
                    t.printStackTrace()
                }
            }
            println("${tests.size - failures.size} / ${tests.size} tests succeeded")
            if (failures.isNotEmpty()) {
                throw AssertionError("Not all tests succeeded")
            }
        }

    }

    @Test
    fun select() = test {
        /* content tests */
        "SELECT ?s ?p ?o WHERE { ?s ?p ?o ; }".satisfies<SelectQueryAST> {
            val pattern = Pattern(
                s = Pattern.Binding("s"),
                p = Pattern.Binding("p"),
                o = Pattern.Binding("o")
            )
            body.patterns.size == 1 && body.patterns.first() == pattern
        }
        "select*{?s?p?o}".satisfies<SelectQueryAST> {
            body.patterns.size == 1
        }
        "prefix ex: <http://example.org/> select*{?s ex:prop ?o}".satisfies<SelectQueryAST> {
            body.patterns.size == 1
        }
        "SELECT * WHERE { ?s a/<predicate2>*/<predicate3>?o. }".satisfies<SelectQueryAST> {
            body.patterns.first().p is Pattern.Chain
        }
        "SELECT * WHERE { ?s a/?p1*/?p2?o. }".satisfies<SelectQueryAST> {
            body.patterns.first().p is Pattern.Chain
                && output.names == setOf("s", "p1", "p2", "o")
                && output.entries.all { it.value is SelectQueryAST.Output.BindingEntry }
        }
        "SELECT * WHERE { ?s (<predicate2>|<predicate3>)?o. }".satisfies<SelectQueryAST> {
            body.patterns.first().p is Pattern.Constrained
        }
        "SELECT * WHERE { ?s <contains>/(<prop1>|!<prop2>)* ?o2 }".satisfies<SelectQueryAST> {
            body.patterns.first().p.let { p -> p is Pattern.Chain && p.list[1] is Pattern.Repeating }
        }
        "SELECT ?s?p?o WHERE {?s?p?o2;?p2?o.}".satisfies<SelectQueryAST> {
            body.patterns.size == 2 && body.patterns[1].p == Pattern.Binding("p2")
        }
        "SELECT ?s WHERE {?s<prop><value>}".satisfies<SelectQueryAST> {
            val pattern = Pattern(
                s = Pattern.Binding("s"),
                p = Pattern.Exact(Triple.NamedTerm("prop")),
                o = Pattern.Exact(Triple.NamedTerm("value"))
            )
            body.patterns.size == 1 && body.patterns.first() == pattern
        }
        "SELECT ?s WHERE{{?s<prop><value>}UNION{?s<prop2><value2>}UNION{?s<prop3><value3>}}".satisfies<SelectQueryAST> {
            body.unions.size == 1 && body.unions.first().size == 3
        }
        "select(count(distinct ?s) as ?count){?s?p?o}".satisfies<SelectQueryAST> {
            // fixme check aggregators
            true
        }
        "select(avg(?s) + min(?s) / 3 as ?count){?s?p?o}".satisfies<SelectQueryAST> {
            // fixme check aggregators
            true
        }
        "select(avg(?s) + min(?s)/3*4+3-5.5*10 as ?count_long){?s?p?o}".satisfies<SelectQueryAST> {
            println(output.aggregate("count_long")!!.root)
            // fixme check aggregators
            true
        }
        """
            PREFIX : <http://example.com/data/#>
            SELECT ?g (AVG(?p) AS ?avg) ((MIN(?p) + MAX(?p)) / 2 AS ?c)
            WHERE {
              ?g :p ?p .
            }
            GROUP BY ?g
        """.satisfies<SelectQueryAST> {
            val pattern = Pattern(
                s = Pattern.Binding("g"),
                p = Pattern.Exact(Triple.NamedTerm("http://example.com/data/#p")),
                o = Pattern.Binding("p")
            )
            body.patterns.size == 1 && body.patterns.first() == pattern
        }
        /* expected failure cases */
        "SELECT TEST WHERE { ?s a TEST . }".shouldFail(CompilerError.Type.SyntaxError)
        "SELECT * WHERE { ?s a ?test ".shouldFail(CompilerError.Type.StructuralError)
        "SELECT * WHERE { ?s <predicate2>/(<predicate3> ?o2.}".shouldFail(CompilerError.Type.StructuralError)
        "SELECT * WHERE { ?s a ?type , }".shouldFail(CompilerError.Type.StructuralError)
        "PREFIX ex: <http://example.org> SELECT * WHERE { ?s ex:prop/other ?o }".shouldFail(CompilerError.Type.SyntaxError)
        "prefix ex: <http://example.org> select*{?s dc:title ?o}".shouldFail(CompilerError.Type.StructuralError)
        "select(count(distinct ?s as ?count){?s?p?o}".shouldFail(CompilerError.Type.StructuralError)
    }

}