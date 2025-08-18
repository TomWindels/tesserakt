package dev.tesserakt.sparql.compiler.analyser

import dev.tesserakt.sparql.compiler.lexer.Token
import dev.tesserakt.sparql.types.Filter

class FilterProcessor: Analyser<Filter>() {

    override fun _process(): Filter {
        // `FILTER` considered consumed
        return when (token) {
            // top level function call, simply processing it as an expression
            is Token.Identifier -> {
                val expr = use(ExpressionProcessor())
                if (token == Token.Symbol.Period) {
                    consume()
                }
                Filter.Predicate(expr)
            }
            // more complex (set of) expression(s)
            Token.Symbol.RoundBracketStart -> {
                consume()
                val expr = use(ExpressionProcessor())
                expectToken(Token.Symbol.RoundBracketEnd)
                consume()
                if (token == Token.Symbol.Period) {
                    consume()
                }
                Filter.Predicate(expr)
            }
            Token.Keyword.Exists -> {
                consume()
                expectToken(Token.Symbol.CurlyBracketStart)
                consume()
                val graph = use(QueryBodyProcessor())
                if (token == Token.Symbol.Period) {
                    consume()
                }
                Filter.Exists(graph)
            }
            Token.Keyword.Not -> {
                consume()
                expectToken(Token.Keyword.Exists)
                consume()
                expectToken(Token.Symbol.CurlyBracketStart)
                consume()
                val graph = use(QueryBodyProcessor())
                if (token == Token.Symbol.Period) {
                    consume()
                }
                Filter.NotExists(graph)
            }
            else -> expectedToken(Token.Symbol.RoundBracketStart, Token.Keyword.Exists, Token.Keyword.Not)
        }
    }

}
