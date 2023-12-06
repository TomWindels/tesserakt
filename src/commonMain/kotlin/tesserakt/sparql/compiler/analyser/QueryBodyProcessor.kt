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
        consumeOrBail()
        when (token) {
            // binding or term, so the start of a block is happening here
            !is Token.Syntax -> {
                builder.addPatterns(use(PatternProcessor()))
            }
            Token.Syntax.CurlyBracketStart -> processSubqueryBody()
            else -> expectedPatternElementOrBindingOrToken(
                Token.Syntax.CurlyBracketStart
            )
        }
    }

    private fun processSubqueryBody() {
        // should be a `{`
        consumeOrBail()
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
            consumeOrBail()
            if (token == Token.Syntax.Union) {
                // continuing
                consumeOrBail()
                expectToken(Token.Syntax.CurlyBracketStart)
                consumeOrBail()
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
