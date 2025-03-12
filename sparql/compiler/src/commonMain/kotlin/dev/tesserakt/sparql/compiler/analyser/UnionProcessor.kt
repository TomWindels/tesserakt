package dev.tesserakt.sparql.compiler.analyser

import dev.tesserakt.sparql.compiler.lexer.Token
import dev.tesserakt.sparql.types.Segment
import dev.tesserakt.sparql.types.Union

class UnionProcessor: Analyser<Union>() {

    private val segments = mutableListOf<Segment>()

    override fun _process(): Union {
        segments.clear()
        expectToken(Token.Symbol.CurlyBracketStart)
        processUnion()
        return Union(segments)
    }

    private fun processUnion() {
        while (true) {
            segments.add(use(SegmentProcessor()))
            if (token == Token.Keyword.Union) {
                // continuing
                consume()
                expectToken(Token.Symbol.CurlyBracketStart)
                // looping back up top
            } else {
                break
            }
        }
    }

}
