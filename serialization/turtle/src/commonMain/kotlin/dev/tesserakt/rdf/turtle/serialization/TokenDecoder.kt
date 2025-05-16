package dev.tesserakt.rdf.turtle.serialization

import dev.tesserakt.rdf.ontology.XSD
import dev.tesserakt.rdf.serialization.InternalSerializationApi
import dev.tesserakt.rdf.serialization.util.*
import dev.tesserakt.util.isNullOr
import kotlin.jvm.JvmInline
import kotlin.text.isWhitespace

// TODO: `source.report()` use
// TODO: improved exception uses

@InternalSerializationApi
@JvmInline
internal value class TokenDecoder(private val source: BufferedString) : Iterator<TurtleToken> {

    override fun hasNext(): Boolean {
        consumeWhitespace()
        return source.peek() != null
    }

    override fun next(): TurtleToken {
        consumeWhitespace()
        TurtleToken.Keyword.CaseSensitive.forEach {
            if (matchesKeyword(it.syntax)) {
                source.consume(it.syntax.length)
                return it
            }
        }
        TurtleToken.Keyword.CaseInsensitive.forEach {
            if (matchesKeywordIgnoreCase(it.syntax)) {
                source.consume(it.syntax.length)
                return it
            }
        }
        val next = source.peek()
        return when {
            next == null -> throw NoSuchElementException("End was reached!")
            next == '<' -> consumeTerm()
            matches("\"\"\"") -> consumeLongLiteralTerm("\"\"\"")
            matches("\'\'\'") -> consumeLongLiteralTerm("\'\'\'")
            next == '"' -> consumeLiteralTerm('"')
            next == '\'' -> consumeLiteralTerm('\'')
            next.isDigit() || next == '+' || next == '-' || (next == '.'  && source.peek(1).isDigit()) ->
                consumeLiteralValue()
            else -> TurtleToken.Structural[next]?.also { source.consume() } ?: consumePrefixedTermOrBail()
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
            .let { EscapeSequenceHelper.decodeNumericEscapes(it) }
        source.consume() // '>'
        // valid non-relative terms start with `mailto:`, `http(s)://`, etc.
        return if (':' !in content) {
            TurtleToken.RelativeTerm(value = content)
        } else {
            TurtleToken.Term(value = content)
        }
    }

    private fun consumeLiteralTerm(terminator: Char): TurtleToken.TermToken {
        check(source.peek() == terminator)
        source.consume() // terminator
        var escaped = false
        val value = consumeWhile { c -> (escaped || c != terminator).also { escaped = !escaped && c == '\\' } }
            .let { EscapeSequenceHelper.decodeNumericEscapes(input = it) }
            .let { EscapeSequenceHelper.decodeMappedCharacterEscapes(input = it) }
        source.consume() // terminator
        if (source.peek() == '@') {
            source.consume()
            // language tag
            val language = consumeLanguageTag()
            return TurtleToken.LocalizedLiteralTerm(value, language)
        } else if (terminator == '"' && source.peek() == '^') {
            source.consume() // '^'
            check(source.peek() == '^')
            source.consume() // '^'
            val type = next()
            check(type is TurtleToken.NonLiteralTerm) { "Invalid literal type: $type" }
            return TurtleToken.LiteralTerm(value, type)
        } else {
            return TurtleToken.LiteralTerm(value, TurtleToken.Term(XSD.string.value))
        }
    }

    private fun consumeLongLiteralTerm(terminator: String): TurtleToken.TermToken {
        check(matches(terminator))
        source.consume(terminator.length)
        var escaped = false
        val value = consumeWhile { c -> (escaped || !matches(terminator)).also { escaped = !escaped && c == '\\' } }
            .let { EscapeSequenceHelper.decodeNumericEscapes(input = it) }
            .let { EscapeSequenceHelper.decodeMappedCharacterEscapes(input = it) }
        source.consume(terminator.length)
        return if (source.peek() == '@') {
            source.consume()
            // language tag
            val language = consumeLanguageTag()
            TurtleToken.LocalizedLiteralTerm(value, language)
        } else {
            TurtleToken.LiteralTerm(value, TurtleToken.Term(XSD.string.value))
        }
    }

    private fun consumeLiteralValue(): TurtleToken.LiteralTerm {
        val result = StringBuilder()
        var next = source.peek()
        while (next != null && (next.isDigit() || next == '.' || next.lowercaseChar() == 'e' || next == '+' || next == '-')) {
            result.append(next)
            source.consume()
            next = source.peek()
        }
        return when {
            !DecimalFormat.matches(result) -> {
                throw IllegalStateException("Invalid numeric literal: `${result}`")
            }

            result.any { it == 'e' || it == 'E' } -> {
                TurtleToken.LiteralTerm(value = result.toString(), type = TurtleToken.Term(XSD.double.value))
            }

            result.any { it == '.' } -> {
                TurtleToken.LiteralTerm(value = result.toString(), type = TurtleToken.Term(XSD.decimal.value))
            }

            else -> {
                TurtleToken.LiteralTerm(value = result.toString(), type = TurtleToken.Term(XSD.int.value))
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
        val value = consumePrefixLocalName()
        return TurtleToken.PrefixedTerm(prefix, value)
    }

    private inline fun consumeLanguageTag(): String {
        // it's invalid to have a dtype present in a language tag
        return consumeWhile { check(!matches("^^")); !it.isWhitespace() }
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
        return true
    }

    /**
     * Returns `true` when [text] matches with the current [source] position, terminated by a whitespace or EOF
     */
    private fun matchesKeyword(text: String): Boolean {
        var i = 0
        while (i < text.length) {
            if (text[i] != source.peek(i)) {
                return false
            }
            ++i
        }
        return source.peek(i).isNullOr { it.isWhitespace() }
    }

    /**
     * Returns `true` when [text] matches with the current [source] position, terminated by a whitespace or EOF
     */
    private fun matchesKeywordIgnoreCase(text: String): Boolean {
        var i = 0
        while (i < text.length) {
            if (text[i].lowercaseChar() != source.peek(i)?.lowercaseChar()) {
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

    /**
     * Consumes, and returns, the prefix local name value, respecting escaping rules.
     * Examples:
     *  * `ex:my\,triple` returns "my,triple"
     */
    private fun consumePrefixLocalName(): String {
        var c = source.peek(0) ?: throw NoSuchElementException("Unexpected EOF reached!")

        fun Char.isTerminatingCharacter(): Boolean = this.isWhitespace() || this == ',' || this == ';' || this == '#'

        val result = StringBuilder()
        var escaped = false

        while (escaped || !c.isTerminatingCharacter()) {
            val isReserved = c in ReservedCharacters
            if (escaped) {
                check(isReserved) { "Invalid character `$c` encountered - unexpected escape!" }
                result.append(c)
                escaped = false
                source.consume()
                c = source.peek() ?: throw NoSuchElementException("Unexpected EOF reached!")
            } else /* !escaped */ {
                if (c == '%' && source.peek(1).isHexDecimal() && source.peek(2).isHexDecimal()) {
                    result.append(source.peek())
                    result.append(source.peek(1))
                    result.append(source.peek(2))
                    source.consume(3)
                    c = source.peek() ?: throw NoSuchElementException("Unexpected EOF reached!")
                } else if (c == '.' && !source.peek(1).isWhitespace()) {
                    result.append('.')
                    result.append(source.peek(1))
                    source.consume(2)
                    c = source.peek() ?: throw NoSuchElementException("Unexpected EOF reached!")
                } else if (c == '\\') {
                    escaped = true
                    source.consume()
                    c = source.peek() ?: throw NoSuchElementException("Unexpected EOF reached!")
                } else if (TurtleToken.Structural[c] == null) {
                    result.append(c)
                    source.consume()
                    c = source.peek() ?: throw NoSuchElementException("Unexpected EOF reached!")
                } else {
                    // non-escaped structural character - leaving
                    break
                }
            }
        }
        return result.toString()
    }

}

private val ReservedCharacters =
    setOf('~', '.', '\\', '-', '!', '\$', '&', '\'', '(', ')', '*', '+', ',', ';', '=', '/', '?', '#', '@', '%', '_')

private val DecimalFormat = Regex("[+-]?\\d*(?:\\.\\d*)?(?:[eE][+-]?\\d*)?")
