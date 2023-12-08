package tesserakt.sparql.compiler.analyser

import tesserakt.sparql.compiler.types.Patterns
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
                is Token.StringLiteral,
                is Token.Binding,
                is Token.NumericLiteral -> {
                    builder.addPatterns(use(PatternProcessor()))
                }
                Token.Syntax.CurlyBracketStart -> processSubqueryBody()
                Token.Syntax.CurlyBracketEnd -> return
                else -> expectedPatternElementOrBindingOrToken(
                    Token.Syntax.CurlyBracketStart,
                    Token.Syntax.CurlyBracketEnd,
                )
            }
        }
        // if this has been reached, the `while` block above hasn't returned, and has thus not been completely
        //  processed
        bail("Unexpected end of input, expected '}'")
    }

    private fun processSubqueryBody() {
        // should be a `{`
        consume()
        when (token) {
            // binding or term, so the start of a block is happening here
            !is Token.Syntax -> processUnion()
            else -> bail("Complex subqueries are currently not supported!")
        }
    }

    private fun processUnion() {
        val patterns = mutableListOf<Patterns>()
        while (true) {
            patterns.add(use(PatternProcessor()))
            expectToken(Token.Syntax.CurlyBracketEnd)
            consume()
            if (token == Token.Syntax.Union) {
                // continuing
                consume()
                expectToken(Token.Syntax.CurlyBracketStart)
                consume()
                expectPatternElementOrBinding()
                // looping back up top
            } else {
                // inserting all patterns and exiting
                builder.addUnion(patterns)
                return
            }
        }
    }

}
