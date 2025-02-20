package dev.tesserakt.rdf.trig.serialization

import dev.tesserakt.rdf.serialization.util.BufferedString
import dev.tesserakt.util.isNullOr
import kotlin.jvm.JvmInline

// TODO: `source.report()` use
// TODO: improved exception uses

@JvmInline
internal value class TokenDecoder(private val source: BufferedString): Iterator<TriGToken> {

    override fun hasNext(): Boolean {
        consumeWhitespace()
        return source.peek() != null
    }

    override fun next(): TriGToken {
        consumeWhitespace()
        TriGToken.Structural.entries.forEach {
            if (matches(it.syntax)) {
                source.consume(it.syntax.length)
                return it
            }
        }
        return when {
            source.peek() == null -> throw NoSuchElementException("End was reached!")
            source.peek() == '<' -> consumeTerm()
            source.peek() == '"' -> consumeLiteralTerm()
            else -> consumePrefixedTermOrBail()
        }
    }

    private fun consumeWhitespace() {
        var next = source.peek()
        while (next != null && next.isWhitespace()) {
            source.consume()
            next = source.peek()
        }
    }

    private fun consumeTerm(): TriGToken.Term {
        check(source.peek() == '<')
        source.consume() // '<'
        val content = consumeWhile { check(!it.isWhitespace()); it != '>' }
        source.consume() // '>'
        return TriGToken.Term(value = content)
    }

    private fun consumeLiteralTerm(): TriGToken.LiteralTerm {
        check(source.peek() == '"')
        source.consume() // '"'
        val value = consumeWhile { it != '"' }
        source.consume() // '"'
        check(source.peek() == '^')
        source.consume() // '^'
        check(source.peek() == '^')
        source.consume() // '^'
        val type = next()
        check(type is TriGToken.NonLiteralTerm) { "Invalid literal type: $type" }
        return TriGToken.LiteralTerm(value, type)
    }

    private fun consumePrefixedTermOrBail(): TriGToken.PrefixedTerm {
        // bailing if we find a whitespace first: invalid term!
        val prefix = consumeWhile { it.isWhitespace() || it != ':' }
        if (source.peek().isNullOr { it.isWhitespace() }) {
            throw IllegalStateException("Invalid term: $prefix")
        }
        source.consume() // ':'
        val value = consumeWhile { !it.isWhitespace() }
            // resolving escape sequences (identical to turtle)
            // https://www.w3.org/TR/turtle/#h_note_2 -> https://www.w3.org/TR/turtle/#reserved
            .replace(EscapeSequence) { it.groupValues[1] }
        return TriGToken.PrefixedTerm(prefix, value)
    }

    /**
     * Returns `true` when [text] matches with the current [source] position, terminated by a whitespace or EOF
     */
    private fun matches(text: String): Boolean {
        var i = 0
        while (i < text.length) {
            if (text[i] != source.peek(i)) {
                return false
            }
            ++i
        }
        return source.peek(i).isNullOr { it.isWhitespace() }
    }

    private inline fun consumeWhile(predicate: (Char) -> Boolean): String {
        val result = StringBuilder()
        while (predicate(source.peek(0) ?: throw NoSuchElementException("Unexpected EOF reached!"))) {
            result.append(source.peek(0))
            source.consume()
        }
        return result.toString()
    }

}

private val EscapeSequence = Regex("\\\\([~.\\-!\$&'()*+,;=/?#@%_])")
