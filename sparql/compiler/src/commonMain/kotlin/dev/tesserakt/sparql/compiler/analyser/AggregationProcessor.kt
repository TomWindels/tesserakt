package dev.tesserakt.sparql.compiler.analyser

import dev.tesserakt.sparql.ast.Aggregation
import dev.tesserakt.sparql.ast.Binding
import dev.tesserakt.sparql.compiler.lexer.Token

/**
 * Processes structures like `(<STATEMENT> AS ?BINDING)`
 */
class AggregationProcessor: Analyser<Aggregation>() {

    override fun _process(): Aggregation {
        // consuming the first `(`
        consume()
        val aggregation = use(AggregatorProcessor())
        // should now be pointing to `AS`, with as next token a binding for the output
        expectToken(Token.Keyword.As)
        consume()
        expectBinding()
        val result = Aggregation(
            expression = aggregation,
            target = Binding((token as Token.Binding).name)
        )
        // consuming it as it is part of the result
        consume()
        // also consuming the closing `)` so the calling processor doesn't interfere with it
        expectToken(Token.Symbol.RoundBracketEnd)
        consume()
        return result
    }

}
