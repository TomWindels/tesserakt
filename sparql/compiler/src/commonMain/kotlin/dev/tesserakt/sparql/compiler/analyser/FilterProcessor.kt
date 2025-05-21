package dev.tesserakt.sparql.compiler.analyser

import dev.tesserakt.sparql.compiler.lexer.Token
import dev.tesserakt.sparql.compiler.lexer.Token.Companion.literalTextValue
import dev.tesserakt.sparql.types.Binding
import dev.tesserakt.sparql.types.Expression
import dev.tesserakt.sparql.types.Filter

class FilterProcessor: Analyser<Filter>() {

    override fun _process(): Filter {
        // `FILTER` considered consumed
        return when (token) {
            Token.Keyword.Regex -> {
                consume()
                expectToken(Token.Symbol.RoundBracketStart)
                consume()
                expectBinding()
                val target = token as Token.Binding
                consume()
                expectToken(Token.Symbol.Comma)
                consume()
                expectStringLiteral()
                val regex = token.literalTextValue
                consume()
                expectToken(Token.Symbol.Comma)
                consume()
                expectStringLiteral()
                val mode = token.literalTextValue
                consume()
                expectToken(Token.Symbol.RoundBracketEnd)
                consume()
                if (token == Token.Symbol.Period) {
                    consume()
                }
                Filter.Regex(
                    input = Binding(target.name),
                    regex = regex,
                    mode = mode
                )
            }
            Token.Symbol.RoundBracketStart -> {
                consume()
                val expr = use(AggregatorProcessor())
                expectToken(Token.Symbol.RoundBracketEnd)
                consume()
                if (token == Token.Symbol.Period) {
                    consume()
                }
                expect(expr is Expression.Comparison)
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
            else -> expectedToken(Token.Keyword.Regex, Token.Symbol.RoundBracketStart, Token.Keyword.Exists, Token.Keyword.Not)
        }
    }

}
