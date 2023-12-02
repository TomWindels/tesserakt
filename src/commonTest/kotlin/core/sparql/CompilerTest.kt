package core.sparql

import core.rdf.SPARQL
import core.rdf.types.Triple
import core.sparql.CompilerTest.Environment.Companion.test
import core.sparql.compiler.CompilerError
import core.sparql.compiler.types.Pattern
import core.sparql.compiler.types.QueryAST
import util.printerrln
import kotlin.test.Test
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

        data class ASTTest(
            override val input: String,
            val test: QueryAST.() -> Boolean
        ): Test() {

            override operator fun invoke() {
                val ast = SPARQL.process(input)
                assertTrue(test(ast), "Validation did not succeed! Got AST $ast")
            }

        }

        data class CompilationFailureTest(
            override val input: String
        ): Test() {

            override operator fun invoke() {
                try {
                    SPARQL.process(input)
                    throw AssertionError("Compilation succeeded unexpectedly!")
                } catch (c: CompilerError) {
                    // exactly what is expected, so not throwing anything
                }
                // all other exceptions are still being thrown
            }

        }

        private val tests = mutableListOf<Test>()

        fun String.satisfies(validation: QueryAST.() -> Boolean) {
            tests.add(ASTTest(input = this, test = validation))
        }

        fun String.shouldFail() {
            tests.add(CompilationFailureTest(input = this))
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
                printerrln("Query $i failed: `${tests[i].input}`")
                if (t is CompilerError) {
                    printerrln(t.stacktrace)
                }
                t.printStackTrace()
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
        "SELECT ?s ?p ?o WHERE { ?s ?p ?o ; }".satisfies {
            val pattern = Pattern(
                s = Pattern.Binding("s"),
                p = Pattern.Binding("p"),
                o = Pattern.Binding("o")
            )
            body.patterns.size == 1 && body.patterns.first() == pattern

        }
        "SELECT * WHERE { ?s a/<predicate2>*/<predicate3>?o. }".satisfies {
            body.patterns.first().p is Pattern.Chain
        }
        "SELECT * WHERE { ?s (<predicate2>|<predicate3>)?o. }".satisfies {
            body.patterns.first().p is Pattern.Constrained
        }
        "SELECT * WHERE { ?s <contains>/(<prop1>|!<prop2>)* ?o2 }".satisfies {
            body.patterns.first().p.let { p -> p is Pattern.Chain && p.list[1] is Pattern.Repeating }
        }
        "SELECT ?s?p?o WHERE {?s?p?o2;?p2?o.}".satisfies {
            body.patterns.size == 2 && body.patterns[1].p == Pattern.Binding("p2")
        }
        "SELECT ?s WHERE {?s<prop><value>}".satisfies {
            val pattern = Pattern(
                s = Pattern.Binding("s"),
                p = Pattern.Exact(Triple.NamedTerm("prop")),
                o = Pattern.Exact(Triple.NamedTerm("value"))
            )
            body.patterns.size == 1 && body.patterns.first() == pattern
        }
        "SELECT ?s WHERE{{?s<prop><value>}UNION{?s<prop2><value2>}UNION{?s<prop3><value3>}}".satisfies {
            body.unions.size == 1 && body.unions.first().size == 3
        }

        /* expected failure cases */
        "SELECT TEST WHERE { ?s a TEST . }".shouldFail()
        "SELECT * WHERE { ?s <predicate2>/(<predicate3> ?o2.}".shouldFail()
        "SELECT * WHERE { ?s a ?type , }".shouldFail()
    }

}
