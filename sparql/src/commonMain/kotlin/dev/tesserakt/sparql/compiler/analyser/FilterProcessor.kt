package dev.tesserakt.sparql.compiler.analyser

import dev.tesserakt.sparql.compiler.lexer.Token
import dev.tesserakt.sparql.compiler.lexer.Token.Companion.literalTextValue
import dev.tesserakt.sparql.types.runtime.element.Expression
import dev.tesserakt.sparql.types.runtime.element.Filter

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
                Filter.Regex(
                    input = target,
                    regex = regex,
                    mode = mode
                )
            }
            Token.Symbol.RoundBracketStart -> {
                consume()
                val expr = use(AggregatorProcessor())
                expectToken(Token.Symbol.RoundBracketEnd)
                consume()
                expect(expr is Expression.Conditional)
                Filter.Predicate(expr)
            }
            else -> expectedToken(Token.Keyword.Regex, Token.Symbol.RoundBracketStart)
        }
    }

}
