package tesserakt.sparql

import tesserakt.rdf.SPARQL
import tesserakt.rdf.types.Triple
import tesserakt.sparql.CompilerTest.Environment.Companion.test
import tesserakt.sparql.compiler.CompilerError
import tesserakt.sparql.compiler.analyser.AggregatorProcessor
import tesserakt.sparql.compiler.processed
import tesserakt.sparql.compiler.types.Aggregation
import tesserakt.sparql.compiler.types.Aggregation.Companion.builtin
import tesserakt.sparql.compiler.types.Aggregation.Companion.distinctBindings
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

            val compilerPackageName = CompilerError::class.qualifiedName!!.removeSuffix(CompilerError::class.simpleName!!)

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
                printerrln("Query ${i + 1} failed: `${tests[i].input.replace(Regex("\\s+"), " ").trim()}`")
                if (t is CompilerError) {
                    printerrln("=== compiler error ===")
                    printerrln(t.message!!)
                    printerrln(t.stacktrace)
                    printerrln("=== shortened stacktrace ===")
                    printerrln(
                        message = t
                            .stackTraceToString()
                            .lineSequence()
                            .takeWhile { it.contains(compilerPackageName) }
                            .joinToString("\n")
                    )
                } else {
                    printerrln("=== stacktrace ===")
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
        "prefix select: <http://example.org/> select*{?s select:prop ?o ; <prop> select:test}".satisfies<SelectQueryAST> {
            body.patterns.size == 2
                && body.patterns[0].p == Pattern.Exact(Triple.NamedTerm("http://example.org/prop"))
                && body.patterns[1].o == Pattern.Exact(Triple.NamedTerm("http://example.org/test"))
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
        """
            SELECT * WHERE {
                ?s a <type>
                {
                    ?s <prop> <value> 
                } UNION {
                    ?s <prop2> <value2>
                } UNION {
                    ?s <prop3> <value3>
                }
            }
        """.satisfies<SelectQueryAST> {
            body.patterns.size == 1 && body.unions.size == 1 && body.unions.first().size == 3
        }
        """
            SELECT * WHERE {
                ?s a <type>
                OPTIONAL { ?s <has> ?content }
                { ?s <prop> ?value1 } UNION { ?s <prop2> <value2> } UNION { ?s <prop3> <value3> }
            }
        """.satisfies<SelectQueryAST> {
            body.patterns.size == 1 &&
            body.optional.size == 1 &&
            body.unions.size == 1 &&
            output.names == setOf("s", "content", "value1")
        }
        """
            SELECT * WHERE {
                ?s <name> ?name .
                # see: https://jena.apache.org/tutorials/sparql_filters.html
                FILTER regex(?name, "test", "i")
            }
        """.satisfies<SelectQueryAST> {
            // TODO: check filter
            true
        }
        """
            SELECT * WHERE {
                ?s <has> ?value .
                # see: https://jena.apache.org/tutorials/sparql_filters.html
                FILTER(?value > 5)
            }
        """.satisfies<SelectQueryAST> {
            // TODO: check filter
            true
        }
        """
            SELECT * WHERE {
                ?s a <type>
                # see: https://jena.apache.org/tutorials/sparql_optionals.html
                OPTIONAL { ?s <has> ?value . FILTER(?value > 5) }
            }
        """.satisfies<SelectQueryAST> {
            body.patterns.size == 1 &&
            body.optional.size == 1 &&
            output.names == setOf("s", "value")
            // TODO: also check the optional's condition
        }
        "select(count(distinct ?s) as ?count){?s?p?o}".satisfies<SelectQueryAST> {
            val func = output.aggregate("count")!!.root.builtin
            func.type == Aggregation.Builtin.Type.COUNT &&
            func.input.distinctBindings == Aggregation.DistinctBindingValues("s")
        }
        "select(avg(?s) + min(?s) / 3 as ?count){?s?p?o}".satisfies<SelectQueryAST> {
            output.aggregate("count")!!.root ==
                    "(${1 / 3.0} * min(?s)) + avg(?s)".processed(AggregatorProcessor()).getOrThrow()
        }
        "select(avg(?s) + min(?s)/3*4+3-5.5*10 as ?count_long){?s?p?o}".satisfies<SelectQueryAST> {
            output.aggregate("count_long")!!.root ==
                    "4 * (min(?s)) / 3 + avg(?s) - 52".processed(AggregatorProcessor()).getOrThrow()
        }
        "select(-min(?s) + max(?s) as ?reversed_diff){?s?p?o}".satisfies<SelectQueryAST> {
            output.aggregate("reversed_diff")!!.root ==
                    "max(?s) - min(?s)".processed(AggregatorProcessor()).getOrThrow()
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
            body.patterns.size == 1 &&
            body.patterns.first() == pattern &&
            output.aggregate("avg")!!.root.builtin.type == Aggregation.Builtin.Type.AVG &&
            output.aggregate("c")!!.root == ".5 * (max(?p) + min(?p))".processed(AggregatorProcessor()).getOrThrow()
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
