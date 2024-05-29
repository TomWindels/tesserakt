package dev.tesserakt.sparql.compiler.analyser

import dev.tesserakt.sparql.compiler.ast.SegmentAST
import dev.tesserakt.sparql.compiler.lexer.Token

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
        return segment
    }

    private fun processSegmentAsQueryBody(): SegmentAST.Statements {
        val content = use(QueryBodyProcessor())
        return SegmentAST.Statements(content)
    }

    private fun processSegmentAsSelectQuery(): SegmentAST.SelectQuery {
        val query = use(SelectQueryProcessor())
        expectToken(Token.Symbol.CurlyBracketEnd)
        consume()
        return SegmentAST.SelectQuery(query)
    }

}
