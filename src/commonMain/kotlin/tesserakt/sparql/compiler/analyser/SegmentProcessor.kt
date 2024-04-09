package tesserakt.sparql.compiler.analyser

import tesserakt.sparql.compiler.types.SegmentAST
import tesserakt.sparql.compiler.types.Token

/**
 * Processes a segment, consuming statements like `{ SELECT ... }` and
 *  `{ <s> <p> <o> OPTIONAL { ... } { ... } UNION { ... } }`
 */
class SegmentProcessor: Analyser<SegmentAST>() {

    override fun _process(): SegmentAST {
        expectToken(Token.Symbol.CurlyBracketStart)
        consume()
        return processSegment()
    }

    private fun processSegment(): SegmentAST {
        val segment = when (token) {
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
        return segment
    }

    private fun processSegmentAsQueryBody(): SegmentAST.Statements {
        val content = use(QueryBodyProcessor())
        return SegmentAST.Statements(content)
    }

    private fun processSegmentAsSelectQuery(): SegmentAST.SelectQuery {
        val query = use(SelectQueryProcessor())
        return SegmentAST.SelectQuery(query)
    }

}