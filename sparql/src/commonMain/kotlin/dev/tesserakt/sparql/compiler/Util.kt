package dev.tesserakt.sparql.compiler

import dev.tesserakt.sparql.compiler.analyser.Analyser
import dev.tesserakt.sparql.compiler.lexer.Lexer
import dev.tesserakt.sparql.compiler.lexer.StringLexer
import dev.tesserakt.sparql.compiler.lexer.Token
import dev.tesserakt.sparql.types.runtime.element.*


internal fun Query.QueryBody.extractAllBindings(): List<Pattern.Binding> =
    (
        patterns.flatMap { pattern -> pattern.extractAllBindings() } +
        unions.flatMap { union -> union.flatMap { it.extractAllBindings() } } +
        optional.flatMap { optional -> optional.segment.extractAllBindings() }
    ).distinct()

fun Segment.extractAllBindings() = when (this) {
    is SelectQuerySegment -> query.extractAllOutputsAsBindings()
    is StatementsSegment -> statements.extractAllBindings()
}

fun SelectQuery.extractAllOutputsAsBindings() =
    output?.map { Pattern.NamedBinding(it.name) } ?: emptyList()

fun Pattern.extractAllBindings(): List<Pattern.Binding> {
    val result = mutableListOf<Pattern.Binding>()
    when (s) {
        is Pattern.Binding -> result.add(s)
        is Pattern.Exact -> { /* nothing to do */ }
    }
    result.addAll(p.extractAllBindings())
    result.addAll(o.extractAllBindings())
    return when (result.size) {
        0 -> emptyList()
        else -> result
    }
}

// helper for the helper

private fun Pattern.Predicate.extractAllBindings(): List<Pattern.Binding> {
    return when (this) {
        is Pattern.Sequence -> chain.flatMap { it.extractAllBindings() }
        is Pattern.UnboundSequence -> chain.flatMap { it.extractAllBindings() }
        is Pattern.Alts -> allowed.flatMap { it.extractAllBindings() }
        is Pattern.SimpleAlts -> allowed.flatMap { it.extractAllBindings() }
        is Pattern.Binding -> listOf(this)
        is Pattern.Exact -> emptyList()
        is Pattern.Negated -> terms.extractAllBindings()
        is Pattern.ZeroOrMore -> element.extractAllBindings()
        is Pattern.OneOrMore -> element.extractAllBindings()
    }
}

private fun Pattern.Object.extractAllBindings(): List<Pattern.Binding> = when (this) {
    is Pattern.Binding -> listOf(this)
    is Pattern.Exact -> { emptyList() }
}

/**
 * Processes the input string `this` using the provided analyser (which has been instantiated in this method). Returns
 *  a result type containing the corresponding AST if the processing was successful, or a result type containing the
 *  compilation error.
 */
fun <RT: RuntimeElement> String.processed(analyser: Analyser<RT>): Result<RT> {
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
