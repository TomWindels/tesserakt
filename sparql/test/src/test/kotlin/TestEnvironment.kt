
import dev.tesserakt.sparql.Compiler.Default.toAST
import dev.tesserakt.sparql.compiler.CompilerError
import dev.tesserakt.sparql.debug.ASTWriter
import dev.tesserakt.sparql.types.runtime.element.Query
import dev.tesserakt.util.printerrln
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestEnvironment private constructor() {

    companion object {

        fun test(block: TestEnvironment.() -> Unit) {
            TestEnvironment().apply(block).test()
        }

    }

    abstract class Test {

        abstract val input: String

        abstract operator fun invoke()

    }

    private val tests = mutableListOf<Test>()

    fun addTest(test: Test) {
        tests.add(test)
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
            val compact = tests[i].input
                .replace(Regex("#.*+\\n?"), "")
                .replace(Regex("\\s+"), " ")
                .trim()
            if (t is CompilerError) {
                printerrln("Query ${i + 1} failed due to a compiler error: `${compact}`\n${t.message}")
            } else {
                printerrln("Query ${i + 1} failed due to an unexpected error: `${compact}`\n${t.message}")
                t.printStackTrace()
            }
        }
        println("${tests.size - failures.size} / ${tests.size} tests succeeded")
        if (failures.isNotEmpty()) {
            val err = AssertionError("Not all tests succeeded")
            failures.forEach { (_, t) -> err.addSuppressed(t) }
            throw err
        }
    }

    data class ASTTest (
        override val input: String,
        val test: Query.() -> Boolean
    ): Test() {

        override operator fun invoke() {
            val ast = input.toAST()
            println("Input:\n$input\nAST:\n${ASTWriter().write(ast)}")
            assertTrue(test(ast), "Validation did not succeed! Got AST $ast")
        }

    }

    data class CompilationFailureTest(
        override val input: String,
        val type: CompilerError.Type
    ): Test() {

        override operator fun invoke() {
            try {
                input.toAST()
                throw AssertionError("Compilation succeeded unexpectedly!")
            } catch (c: CompilerError) {
                // exactly what is expected, so not throwing anything
                assertEquals(c.type, type, "Compilation failed for a different reason!")
            }
            // all other exceptions are still being thrown
        }

    }

    infix fun String.satisfies(validation: Query.() -> Boolean) {
        addTest(ASTTest(input = this, test = validation))
    }

    infix fun String.causes(error: CompilerError.Type) {
        addTest(CompilationFailureTest(input = this, type = error))
    }

}
