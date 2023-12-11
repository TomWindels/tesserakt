package tesserakt

import tesserakt.sparql.Compiler.Default.toAST
import tesserakt.sparql.compiler.CompilerError
import tesserakt.sparql.compiler.types.QueryAST
import tesserakt.util.printerrln
import kotlin.reflect.KClass
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
            val err = AssertionError("Not all tests succeeded")
            failures.forEach { (_, t) -> err.addSuppressed(t) }
            throw err
        }
    }

    data class ASTTest <Q: QueryAST> (
        override val input: String,
        val test: Q.() -> Boolean,
        val clazz: KClass<Q>
    ): Test() {

        override operator fun invoke() {
            val ast = input.toAST()
            require(clazz.isInstance(ast))
            @Suppress("UNCHECKED_CAST")
            assertTrue(test(ast as Q), "Validation did not succeed! Got AST $ast")
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

    inline fun <reified Q: QueryAST> String.satisfies(noinline validation: Q.() -> Boolean) {
        addTest(ASTTest(input = this, test = validation, clazz = Q::class))
    }

    fun String.shouldFail(error: CompilerError.Type) {
        addTest(CompilationFailureTest(input = this, type = error))
    }

}
