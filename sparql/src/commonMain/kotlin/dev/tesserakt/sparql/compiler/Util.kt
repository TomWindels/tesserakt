package dev.tesserakt.sparql.compiler

import dev.tesserakt.sparql.compiler.analyser.Analyser
import dev.tesserakt.sparql.compiler.ast.*
import dev.tesserakt.sparql.compiler.lexer.Lexer
import dev.tesserakt.sparql.compiler.lexer.StringLexer
import dev.tesserakt.sparql.compiler.lexer.Token


internal fun QueryAST.QueryBodyAST.extractAllBindings(): List<PatternAST.Binding> =
    (
        patterns.flatMap { pattern -> pattern.extractAllBindings() } +
        unions.flatMap { union -> union.flatMap { it.extractAllBindings() } } +
        optionals.flatMap { optional -> optional.patterns.flatMap { it.extractAllBindings() } }
    ).distinct()

fun SegmentAST.extractAllBindings() = when (this) {
    is SegmentAST.SelectQuery -> query.extractAllOutputsAsBindings()
    is SegmentAST.Statements -> statements.extractAllBindings()
}

fun SelectQueryAST.extractAllOutputsAsBindings() = output.keys.map { PatternAST.Binding(it) }

fun PatternAST.extractAllBindings(): List<PatternAST.Binding> {
    val result = mutableListOf<PatternAST.Binding>()
    when (s) {
        is PatternAST.Binding -> result.add(s)
        is PatternAST.Exact -> { /* nothing to do */ }
    }
    result.addAll(p.extractAllBindings())
    result.addAll(o.extractAllBindings())
    return when (result.size) {
        0 -> emptyList()
        else -> result
    }
}

// helper for the helper

private fun PatternAST.Predicate.extractAllBindings(): List<PatternAST.Binding> {
    return when (this) {
        is PatternAST.Chain -> chain.flatMap { it.extractAllBindings() }
        is PatternAST.Alts -> allowed.flatMap { it.extractAllBindings() }
        is PatternAST.Binding -> listOf(this)
        is PatternAST.Exact -> emptyList()
        is PatternAST.Not -> predicate.extractAllBindings()
        is PatternAST.ZeroOrMore -> value.extractAllBindings()
        is PatternAST.OneOrMore -> value.extractAllBindings()
    }
}

private fun PatternAST.Object.extractAllBindings(): List<PatternAST.Binding> = when (this) {
    is PatternAST.BlankObject -> properties.flatMap { it.extractAllBindings() }
    is PatternAST.Binding -> listOf(this)
    is PatternAST.Exact -> { emptyList() }
}

private fun PatternAST.BlankObject.BlankPattern.extractAllBindings(): List<PatternAST.Binding> {
    val first = p.extractAllBindings()
    val second = o.extractAllBindings()
    return if (first.isEmpty() && second.isEmpty()) {
        emptyList()
    } else {
        first + second
    }
}

/**
 * Processes the input string `this` using the provided analyser (which has been instantiated in this method). Returns
 *  a result type containing the corresponding AST if the processing was successful, or a result type containing the
 *  compilation error.
 */
fun <RT: ASTNode> String.processed(analyser: Analyser<RT>): Result<RT> {
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
