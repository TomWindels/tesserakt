package dev.tesserakt.sparql.compiler.analyser

import dev.tesserakt.sparql.compiler.lexer.Token
import dev.tesserakt.sparql.compiler.lexer.Token.Companion.bindingName
import dev.tesserakt.sparql.types.Binding
import dev.tesserakt.sparql.types.Ordering

class OrderProcessor: Analyser<Ordering>() {

    override fun _process(): Ordering {
        expectToken(Token.Keyword.Order)
        consume()
        expectToken(Token.Keyword.By)
        consume()
        val elements = processElements()
        if (elements.isEmpty()) {
            bail("Expected at least one binding to use for ordering")
        }
        return Ordering(elements)
    }

    private fun processElements(): List<Ordering.Element> {
        return buildList {
            while (true) {
                add(processElement() ?: return@buildList)
            }
        }
    }

    private fun processElement(): Ordering.Element? {
        val mode = when (val current = token) {
            is Token.Binding -> {
                consume()
                return Ordering.Element(binding = Binding(current.name), mode = Ordering.Element.Mode.Ascending)
            }
            Token.Keyword.Asc -> {
                consume()
                Ordering.Element.Mode.Ascending
            }
            Token.Keyword.Desc -> {
                consume()
                Ordering.Element.Mode.Descending
            }
            else -> return null
        }
        expectToken(Token.Symbol.RoundBracketStart)
        consume()
        expectBinding()
        val binding = Binding(token.bindingName)
        consume()
        expectToken(Token.Symbol.RoundBracketEnd)
        consume()
        return Ordering.Element(binding = binding, mode = mode)
    }

}
