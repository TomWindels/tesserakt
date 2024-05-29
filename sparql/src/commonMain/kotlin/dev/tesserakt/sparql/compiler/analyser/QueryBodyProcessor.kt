package dev.tesserakt.sparql.compiler.analyser

import dev.tesserakt.sparql.compiler.ast.QueryAST
import dev.tesserakt.sparql.compiler.lexer.Token

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
                Token.Keyword.Optional -> {
                    // consuming the "OPTIONAL" keyword before extracting the segment
                    consume()
                    // extracting the segment and inserting it
                    builder.addOptional(use(SegmentProcessor()))
                }
                Token.Symbol.CurlyBracketStart -> {
                    builder.addUnion(use(UnionProcessor()))
                }
                Token.Symbol.CurlyBracketEnd -> {
                    // done, leaving the `}` alone
                    return
                }
                else -> expectedPatternElementOrBindingOrToken(
                    Token.Keyword.Optional,
                    Token.Symbol.CurlyBracketStart,
                    Token.Symbol.CurlyBracketEnd,
                )
            }
        }
        // if this has been reached, the `while` block above hasn't returned, and has thus not been completely
        //  processed
        bail("Unexpected end of input, expected '}'")
    }

}
