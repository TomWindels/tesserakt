package tesserakt.sparql.compiler

import tesserakt.sparql.compiler.analyser.Analyser
import tesserakt.sparql.compiler.lexer.Lexer
import tesserakt.sparql.compiler.lexer.StringLexer
import tesserakt.sparql.compiler.types.Pattern
import tesserakt.sparql.compiler.types.Token

fun Pattern.bindings(): List<Pattern.Binding> {
    val result = mutableListOf<Pattern.Binding>()
    if (s is Pattern.Binding) {
        result.add(s)
    }
    result.addAll(p.bindings())
    if (o is Pattern.Binding) {
        result.add(o)
    }
    return when (result.size) {
        0 -> emptyList()
        else -> result
    }
}

// helper for the helper

private fun Pattern.Predicate.bindings(): List<Pattern.Binding> {
    return when (this) {
        is Pattern.Chain -> list.flatMap { it.bindings() }
        is Pattern.Constrained -> allowed.flatMap { it.bindings() }
        is Pattern.Binding -> listOf(this)
        is Pattern.Exact -> emptyList()
        is Pattern.Not -> predicate.bindings()
        is Pattern.Repeating -> value.bindings()
    }
}

/**
 * Processes the input string `this` using the provided analyser (which has been instantiated in this method). Returns
 *  a result type containing the corresponding AST if the processing was successful, or a result type containing the
 *  compilation error.
 */
fun <AST> String.processed(analyser: Analyser<AST>): Result<AST> {
    return try {
        val lexer = StringLexer(this)
        val ast = analyser.configureAndUse(lexer)
        // throwing if the end hasn't been reached yet
        lexer.assertEndOfInput()
        Result.success(ast)
    } catch (c: CompilerError) {
        Result.failure(c)
    }
    // all other exceptions can still be thrown, which is intentional as they aren't
    // supposed to happen
}

/**
 * Asserts that the lexer has reached the end of the input. Throws a compilation error otherwise.
 */
fun Lexer.assertEndOfInput() {
    if (current != Token.EOF) {
        throw CompilerError(
            message = "End of input expected",
            type = CompilerError.Type.StructuralError,
            stacktrace = stacktrace(
                type = CompilerError.Type.StructuralError,
                message = "Unexpected token $current, end of input expected"
            )
        )
    }
}
