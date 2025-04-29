package dev.tesserakt.rdf.turtle.serialization

import dev.tesserakt.rdf.ontology.RDF
import dev.tesserakt.rdf.ontology.XSD
import dev.tesserakt.rdf.serialization.util.BufferedString
import dev.tesserakt.util.isNullOr
import kotlin.jvm.JvmInline

// TODO: `source.report()` use
// TODO: improved exception uses

@JvmInline
internal value class TokenDecoder(private val source: BufferedString): Iterator<TurtleToken> {

    override fun hasNext(): Boolean {
        consumeWhitespace()
        return source.peek() != null
    }

    override fun next(): TurtleToken {
        consumeWhitespace()
        TurtleToken.Structural.entries.forEach {
            if (matches(it.syntax)) {
                source.consume(it.syntax.length)
                return it
            }
        }
        val next = source.peek()
        return when {
            next == null -> throw NoSuchElementException("End was reached!")
            next == '<' -> consumeTerm()
            next == '"' -> consumeLiteralTerm()
            next.isDigit() -> consumeLiteralValue()
            else -> consumePrefixedTermOrBail()
        }
    }

    private fun consumeWhitespace() {
        var next = source.peek()
        var inComment = next == '#'
        while (next != null && (next.isWhitespace() || inComment)) {
            source.consume()
            next = source.peek()
            inComment = (inComment && next != '\n') || next == '#'
        }
    }

    private fun consumeTerm(): TurtleToken.TermToken {
        check(source.peek() == '<')
        source.consume() // '<'
        val content = consumeWhile { check(!it.isWhitespace()); it != '>' }
        source.consume() // '>'
        // valid non-relative terms start with `mailto:`, `http(s)://`, etc.
        return if (':' !in content) {
            TurtleToken.RelativeTerm(value = content)
        } else {
            TurtleToken.Term(value = content)
        }
    }

    private fun consumeLiteralTerm(): TurtleToken.LiteralTerm {
        check(source.peek() == '"')
        source.consume() // '"'
        val value = consumeWhile { it != '"' }
        source.consume() // '"'
        if (source.peek() == '^') {
            source.consume() // '^'
            check(source.peek() == '^')
            source.consume() // '^'
            val type = next()
            check(type is TurtleToken.NonLiteralTerm) { "Invalid literal type: $type" }
            return TurtleToken.LiteralTerm(value, type)
        } else if (source.peek() == '@') {
            // language tag, ignored for now
            // FIXME use the language tag
            consumeWhile { !it.isWhitespace() }
            return TurtleToken.LiteralTerm(value, TurtleToken.Term(RDF.langString.value))
        } else {
            return TurtleToken.LiteralTerm(value, TurtleToken.Term(XSD.string.value))
        }
    }

    private fun consumeLiteralValue(): TurtleToken.LiteralTerm {
        val result = StringBuilder()
        var next = source.peek()
        while (next != null && next.isDigit() || next == '.') {
            result.append(next)
            source.consume()
            next = source.peek()
        }
        return when (result.count { it == '.' }) {
            1 -> {
                TurtleToken.LiteralTerm(value = result.toString(), type = TurtleToken.Term(XSD.double.value))
            }
            0 -> {
                TurtleToken.LiteralTerm(value = result.toString(), type = TurtleToken.Term(XSD.int.value))
            }
            else -> {
                throw IllegalStateException("Invalid numeric literal: `${result}`")
            }
        }
    }

    private fun consumePrefixedTermOrBail(): TurtleToken.PrefixedTerm {
        // bailing if we find a whitespace first: invalid term!
        val prefix = consumeWhile { it.isWhitespace() || it != ':' }
        if (source.peek().isNullOr { it.isWhitespace() }) {
            throw IllegalStateException("Invalid term: $prefix")
        }
        source.consume() // ':'
        val value = consumeWhile { it.isValidPrefixCharacter() }
            // resolving escape sequences (identical to turtle)
            // https://www.w3.org/TR/turtle/#h_note_2 -> https://www.w3.org/TR/turtle/#reserved
            .replace(EscapeSequence) { it.groupValues[1] }
        return TurtleToken.PrefixedTerm(prefix, value)
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
        while (predicate(source.peek(0) ?: throw NoSuchElementException("Unexpected EOF reached! Last received data: `$result`"))) {
            result.append(source.peek(0))
            source.consume()
        }
        return result.toString()
    }

    // FIXME: support escape sequences
    private fun Char.isValidPrefixCharacter(): Boolean {
        return !isWhitespace() && this != ',' && this != ';' && this != '.'
    }
}

private val EscapeSequence = Regex("\\\\([~.\\-!\$&'()*+,;=/?#@%_])")
