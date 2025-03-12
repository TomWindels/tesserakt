package dev.tesserakt.sparql.debug

import dev.tesserakt.sparql.compiler.lexer.Token
import dev.tesserakt.sparql.types.QueryAtom

object StylisedWriter: QueryWriter<String>() {

    private val state = State()

    data class State(
        val content: StylisedString = StylisedString(),
        var indent: Int = 0
    ) {
        fun clear() {
            content.clear()
            indent = 0
        }
    }

    override fun write(element: QueryAtom): String {
        process(element)
        // considering the stylised string type acts as a builder, clearing it without converting it to a regular
        //  string first would undo all processing
        return state.content.toString()
            .also { state.clear() }
    }

    override fun newline() {
        state.content.add('\n')
        state.content.add("\t".repeat(state.indent))
    }

    override fun add(token: Token) {
        state.content.add(token.stringified())
        state.content.add(' ')
    }

    override fun indent() {
        state.indent += 1
    }

    override fun unindent() {
        state.indent -= 1
    }

    /* helpers */

    private fun Token.stringified(): StylisedString = buildStylisedString {
        when (this@stringified) {
            is Token.Term -> {
                add("<$value>", Color.CYAN)
            }
            is Token.PrefixedTerm -> {
                add("$namespace:$value", Color.CYAN)
            }
            is Token.BlankTerm -> {
                add("_:$value", Color.BRIGHT_BLACK)
            }
            is Token.Binding -> {
                add("?$name", Color.BLUE)
            }
            is Token.NumericLiteral -> {
                add(value.toString(), Color.GREEN)
            }
            is Token.StringLiteral -> {
                add(value, Color.GREEN)
            }
            is Token.Symbol -> {
                add(syntax, Color.WHITE)
            }
            is Token.Keyword -> {
                add(syntax, Color.MAGENTA)
            }
            Token.EOF -> { /* not expected to happen */ }
        }
    }

}
