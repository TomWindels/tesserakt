package tesserakt.sparql.compiler.analyser

import tesserakt.sparql.compiler.types.Token
import tesserakt.sparql.compiler.types.UnionAST

class UnionProcessor: Analyser<UnionAST>() {

    private val segments = mutableListOf<UnionAST.Segment>()

    override fun _process(): UnionAST {
        segments.clear()
        expectToken(Token.Symbol.CurlyBracketStart)
        consume()
        processUnion()
        return UnionAST(segments)
    }

    private fun processUnion() {
        while (true) {
            when (token) {
                // all possible element types representing a statement section
                is Token.Term,
                is Token.PrefixedTerm,
                is Token.StringLiteral,
                is Token.Binding,
                is Token.NumericLiteral,
                Token.Keyword.Optional,
                Token.Symbol.CurlyBracketStart -> processSegmentAsQueryBody()

                // representing a subquery
                Token.Keyword.Select -> processSegmentAsSelectQuery()

                else -> expectedPatternElementOrBindingOrToken(
                    Token.Keyword.Select,
                    Token.Keyword.Optional,
                    Token.Symbol.CurlyBracketStart
                )
            }
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
    }

    private fun processSegmentAsQueryBody() {
        val content = use(QueryBodyProcessor())
        segments.add(UnionAST.StatementsSegment(content))
    }

    private fun processSegmentAsSelectQuery() {
        val query = use(SelectQueryProcessor())
        segments.add(UnionAST.SelectQuerySegment(query))
    }

}
