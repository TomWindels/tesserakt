package dev.tesserakt.sparql.compiler.analyser

import dev.tesserakt.sparql.compiler.types.Aggregation
import dev.tesserakt.sparql.compiler.types.Token

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
            root = aggregation,
            output = token as Token.Binding
        )
        // consuming it as it is part of the result
        consume()
        // also consuming the closing `)` so the calling processor doesn't interfere with it
        expectToken(Token.Symbol.RoundBracketEnd)
        consume()
        return result
    }

}
