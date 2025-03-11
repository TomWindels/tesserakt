package dev.tesserakt.sparql.compiler

import dev.tesserakt.sparql.ast.*
import dev.tesserakt.sparql.compiler.analyser.Analyser
import dev.tesserakt.sparql.compiler.lexer.Lexer
import dev.tesserakt.sparql.compiler.lexer.StringLexer
import dev.tesserakt.sparql.compiler.lexer.Token


internal fun GraphPattern.extractAllBindings(): List<TriplePattern.Binding> =
    (
        patterns.flatMap { pattern -> pattern.extractAllBindings() } +
        unions.flatMap { union -> union.flatMap { it.extractAllBindings() } } +
        optional.flatMap { optional -> optional.segment.extractAllBindings() }
    ).distinct()

fun Segment.extractAllBindings() = when (this) {
    is SelectQuerySegment -> query.extractAllOutputsAsBindings()
    is GraphPatternSegment -> pattern.extractAllBindings()
}

fun CompiledSelectQuery.extractAllOutputsAsBindings() =
    output?.map { TriplePattern.NamedBinding(it.name) } ?: emptyList()

fun TriplePattern.extractAllBindings(): List<TriplePattern.Binding> {
    val result = mutableListOf<TriplePattern.Binding>()
    when (s) {
        is TriplePattern.Binding -> result.add(s)
        is TriplePattern.Exact -> { /* nothing to do */ }
    }
    result.addAll(p.extractAllBindings())
    result.addAll(o.extractAllBindings())
    return when (result.size) {
        0 -> emptyList()
        else -> result
    }
}

// helper for the helper

private fun TriplePattern.Predicate.extractAllBindings(): List<TriplePattern.Binding> {
    return when (this) {
        is TriplePattern.Sequence -> chain.flatMap { it.extractAllBindings() }
        is TriplePattern.UnboundSequence -> chain.flatMap { it.extractAllBindings() }
        is TriplePattern.Alts -> allowed.flatMap { it.extractAllBindings() }
        is TriplePattern.SimpleAlts -> allowed.flatMap { it.extractAllBindings() }
        is TriplePattern.Binding -> listOf(this)
        is TriplePattern.Exact -> emptyList()
        is TriplePattern.Negated -> terms.extractAllBindings()
        is TriplePattern.ZeroOrMore -> element.extractAllBindings()
        is TriplePattern.OneOrMore -> element.extractAllBindings()
    }
}

private fun TriplePattern.Object.extractAllBindings(): List<TriplePattern.Binding> = when (this) {
    is TriplePattern.Binding -> listOf(this)
    is TriplePattern.Exact -> { emptyList() }
}

/**
 * Processes the input string `this` using the provided analyser (which has been instantiated in this method). Returns
 *  a result type containing the corresponding AST if the processing was successful, or a result type containing the
 *  compilation error.
 */
fun <RT: QueryAtom> String.processed(analyser: Analyser<RT>): Result<RT> {
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
