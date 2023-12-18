package tesserakt.sparql.compiler.analyser

import tesserakt.sparql.compiler.types.PatternsAST
import tesserakt.sparql.compiler.types.QueryAST
import tesserakt.sparql.compiler.types.Token

class QueryBodyProcessor: Analyser<QueryAST.QueryBodyAST>() {

    private val builder = QueryAST.QueryBodyASTBuilder()

    override fun _process(): QueryAST.QueryBodyAST {
        processQueryBody()
        return builder.build()
    }

    private fun processQueryBody() {
        // consuming the starting `{`
        consume()
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
                Token.Keyword.Optional -> processOptional()
                Token.Symbol.CurlyBracketStart -> processSubsectionBody()
                Token.Symbol.CurlyBracketEnd -> {
                    consume()
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

    private fun processSubsectionBody() {
        // should be a `{`
        consume()
        when (token) {
            // binding or term, so the start of a block is happening here
            is Token.Term,
            is Token.PrefixedTerm,
            is Token.StringLiteral,
            is Token.Binding,
            is Token.NumericLiteral -> processUnion()
            else -> bail("Complex subqueries are currently not supported!")
        }
    }

    private fun processOptional() {
        // consuming the "optional {"
        consume()
        expectToken(Token.Symbol.CurlyBracketStart)
        consume()
        // extracting all patterns and inserting them
        builder.addOptional(use(PatternProcessor()))
        // consuming the final part
        expectToken(Token.Symbol.CurlyBracketEnd)
        consume()
    }

    private fun processUnion() {
        val patterns = mutableListOf<PatternsAST>()
        while (true) {
            patterns.add(use(PatternProcessor()))
            expectToken(Token.Symbol.CurlyBracketEnd)
            consume()
            if (token == Token.Keyword.Union) {
                // continuing
                consume()
                expectToken(Token.Symbol.CurlyBracketStart)
                consume()
                // looping back up top
            } else {
                break
            }
        }
        if (patterns.size == 1) {
            // the processed structure looks like `{ patterns }`, no union keyword was ever used, so adding them
            //  outright
            builder.addPatterns(patterns.first())
        } else if (patterns.size > 1) {
            // inserting all patterns and exiting
            builder.addUnion(patterns)
        }
    }

}
