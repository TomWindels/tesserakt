package dev.tesserakt.sparql.compiler.analyser

import dev.tesserakt.sparql.compiler.ast.ExpressionAST
import dev.tesserakt.sparql.compiler.ast.QueryAST
import dev.tesserakt.sparql.compiler.lexer.Token
import dev.tesserakt.sparql.compiler.lexer.Token.Companion.bindingName

class QueryBodyProcessor: Analyser<QueryAST.QueryBodyAST>() {

    private val builder = QueryAST.QueryBodyASTBuilder()

    override fun _process(): QueryAST.QueryBodyAST {
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
                    builder.addPatterns(use(PatternProcessor()))
                }
                Token.Keyword.Filter -> {
                    consume()
                    builder.addFilter(use(FilterProcessor()))
                }
                Token.Keyword.Optional -> {
                    // consuming the "OPTIONAL" keyword before extracting the segment
                    consume()
                    // also consuming its brackets
                    expectToken(Token.Symbol.CurlyBracketStart)
                    consume()
                    // extracting the segment and inserting it
                    builder.addOptional(use(PatternProcessor()))
                    // now eating the closing syntax
                    expectToken(Token.Symbol.CurlyBracketEnd)
                    consume()
                }
                Token.Symbol.CurlyBracketStart -> {
                    builder.addUnion(use(UnionProcessor()))
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
                    builder.addBindStatement(ExpressionAST.BindingStatement(expression = expression, target = target))
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
