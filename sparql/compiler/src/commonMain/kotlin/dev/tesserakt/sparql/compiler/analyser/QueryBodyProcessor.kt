package dev.tesserakt.sparql.compiler.analyser

import dev.tesserakt.sparql.compiler.lexer.Token
import dev.tesserakt.sparql.compiler.lexer.Token.Companion.bindingName
import dev.tesserakt.sparql.types.*

class QueryBodyProcessor: Analyser<GraphPattern>() {

    private data class Builder(
        /** The full pattern block that is required **/
        val patterns: MutableList<TriplePattern> = mutableListOf(),
        /** All binding statements found inside this pattern block (similar to filters) **/
        val bindingStatements: MutableList<BindingStatement> = mutableListOf(),
        /** All filters applied to this pattern block (optional / union filters NOT included) **/
        val filters: MutableList<Filter> = mutableListOf(),
        /** All requested unions, not yet flattened to allow for easier optimisation **/
        val unions: MutableList<Union> = mutableListOf(),
        /** Collection of pattern blocks that are optional **/
        val optional: MutableList<Optional> = mutableListOf()
    ) {
        fun build() = GraphPattern(
            patterns = TriplePatternSet(patterns),
            bindingStatements = bindingStatements,
            filters = filters,
            unions = unions,
            optional = optional
        )
    }

    private val builder = Builder()

    override fun _process(): GraphPattern {
        processQueryBody()
        return builder.build()
    }

    private fun processQueryBody() {
        // assuming the starting `{` has been consumed already
        while (token != Token.EOF) {
            when (token) {
                // binding or term, so the start of a block is happening here
                is Token.Term,
                is Token.PrefixedTerm,
                is Token.StringLiteral,
                is Token.Binding,
                is Token.NumericLiteral -> {
                    builder.patterns.addAll(use(PatternProcessor()))
                }
                Token.Keyword.Filter -> {
                    consume()
                    builder.filters.add(use(FilterProcessor()))
                }
                Token.Keyword.Optional -> {
                    // consuming the "OPTIONAL" keyword before extracting the segment
                    consume()
                    // extracting the segment and inserting it
                    builder.optional.add(Optional(use(SegmentProcessor())))
                }
                Token.Symbol.CurlyBracketStart -> {
                    builder.unions.add(use(UnionProcessor()))
                }
                Token.Symbol.CurlyBracketEnd -> {
                    // done, consuming it and returning
                    consume()
                    return
                }
                Token.Keyword.Bind -> {
                    consume()
                    expectToken(Token.Symbol.RoundBracketStart)
                    consume()
                    val expression = use(AggregatorProcessor())
                    expectToken(Token.Keyword.As)
                    consume()
                    expectBinding()
                    val target = token.bindingName
                    consume()
                    expectToken(Token.Symbol.RoundBracketEnd)
                    consume()
                    builder.bindingStatements.add(BindingStatement(expression = expression, target = Binding(target)))
                }
                else -> expectedPatternElementOrBindingOrToken(
                    Token.Keyword.Filter,
                    Token.Keyword.Optional,
                    Token.Keyword.Bind,
                    Token.Symbol.CurlyBracketStart,
                    Token.Symbol.CurlyBracketEnd
                )
            }
        }
        // if this has been reached, the `while` block above hasn't returned, and has thus not been completely
        //  processed
        bail("Unexpected end of input, expected '}'")
    }

}
