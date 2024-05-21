package dev.tesserakt.sparql.compiler.analyser

import dev.tesserakt.sparql.compiler.types.SegmentAST
import dev.tesserakt.sparql.compiler.types.Token
import dev.tesserakt.sparql.compiler.types.UnionAST

class UnionProcessor: Analyser<UnionAST>() {

    private val segments = mutableListOf<SegmentAST>()

    override fun _process(): UnionAST {
        segments.clear()
        expectToken(Token.Symbol.CurlyBracketStart)
        processUnion()
        return UnionAST(segments)
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
