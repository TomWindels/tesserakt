package dev.tesserakt.sparql.compiler.analyser

import dev.tesserakt.sparql.compiler.lexer.Token
import dev.tesserakt.sparql.types.runtime.element.Segment
import dev.tesserakt.sparql.types.runtime.element.SelectQuerySegment
import dev.tesserakt.sparql.types.runtime.element.StatementsSegment

/**
 * Processes a segment, consuming statements like `{ SELECT ... }` and
 *  `{ <s> <p> <o> OPTIONAL { ... } { ... } UNION { ... } }`
 */
class SegmentProcessor: Analyser<Segment>() {

    override fun _process(): Segment {
        expectToken(Token.Symbol.CurlyBracketStart)
        consume()
        return processSegment()
    }

    private fun processSegment(): Segment {
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

    private fun processSegmentAsQueryBody(): StatementsSegment {
        val content = use(QueryBodyProcessor())
        return StatementsSegment(content)
    }

    private fun processSegmentAsSelectQuery(): SelectQuerySegment {
        val query = use(SelectQueryProcessor())
        expectToken(Token.Symbol.CurlyBracketEnd)
        consume()
        return SelectQuerySegment(query)
    }

}
