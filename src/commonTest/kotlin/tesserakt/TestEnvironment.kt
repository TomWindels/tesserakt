package tesserakt

import tesserakt.sparql.SPARQL
import tesserakt.sparql.compiler.CompilerError
import tesserakt.sparql.compiler.types.QueryAST
import tesserakt.util.printerrln
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestEnvironment private constructor() {

    companion object {

        val compilerPackageName = CompilerError::class.qualifiedName!!.removeSuffix(CompilerError::class.simpleName!!)

        fun test(block: TestEnvironment.() -> Unit) {
            TestEnvironment().apply(block).test()
        }

    }

    abstract class Test {

        abstract val input: String

        abstract operator fun invoke()

    }

    private val tests = mutableListOf<Test>()

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
            printerrln(
                "Query ${i + 1} failed: `${tests[i].input.replace(Regex("\\s+"), " ").trim()}`"
            )
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

    data class ASTTest <Q: QueryAST> (
        override val input: String,
        val test: Q.() -> Boolean
    ): TestEnvironment.Test() {

        override operator fun invoke() {
            @Suppress("UNCHECKED_CAST")
            val ast = SPARQL.process(input) as Q
            assertTrue(test(ast), "Validation did not succeed! Got AST $ast")
        }

    }

    data class CompilationFailureTest(
        override val input: String,
        val type: CompilerError.Type
    ): TestEnvironment.Test() {

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

    fun <Q: QueryAST> String.satisfies(validation: Q.() -> Boolean) {
        tests.add(ASTTest(input = this, test = validation))
    }

    fun String.shouldFail(error: CompilerError.Type) {
        tests.add(CompilationFailureTest(input = this, type = error))
    }

}
