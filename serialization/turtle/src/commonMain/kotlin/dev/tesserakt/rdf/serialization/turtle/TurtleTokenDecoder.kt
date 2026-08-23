package dev.tesserakt.rdf.serialization.turtle

import dev.tesserakt.rdf.ontology.XSD
import dev.tesserakt.rdf.serialization.InternalSerializationApi
import dev.tesserakt.rdf.serialization.util.*
import kotlin.text.isWhitespace

@InternalSerializationApi
internal class TurtleTokenDecoder(private val source: BufferedCharStream) : Iterator<TurtleToken> {

    override fun hasNext(): Boolean {
        consumeWhitespace()
        return source.peek() != -1
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
        val code = source.peek()
        if (code == -1) {
            throw NoSuchElementException("End was reached!")
        }
        val next = Char(code)
        return when {
            next == '<' -> {
                consumeTerm()
            }
            matches("\"\"\"") -> {
                consumeLongLiteralTerm("\"\"\"")
            }
            matches("\'\'\'") -> {
                consumeLongLiteralTerm("\'\'\'")
            }
            next == '"' -> {
                consumeLiteralTerm('"')
            }
            next == '\'' -> {
                consumeLiteralTerm('\'')
            }
            next.isDigit() ||
            next == '+' ||
            next == '-' ||
            (next == '.'  && source.peek(1).let { it != -1 && Char(it).isDigit() }) -> {
                consumeLiteralValue()
            }
            next == 'a' && source.peek(1).let { it == -1 || Char(it).isWhitespace() || it == '<'.code } -> {
                TurtleToken.Keyword.TypePredicate.also { source.consume() }
            }
            else -> {
                TurtleToken.Structural[next]?.also { source.consume() } ?: consumePrefixedTermOrBail()
            }
        }
    }

    override fun toString() = "TokenDecoder { source: $source }"

    private fun consumeWhitespace() {
        var nextCode = source.peek()
        if (nextCode == -1) {
            return
        }
        var next = Char(nextCode)
        var inComment = next == '#'
        while (nextCode != -1 && (next.isWhitespace() || inComment)) {
            source.consume()
            nextCode = source.peek()
            if (nextCode == -1) {
                return
            }
            next = Char(nextCode)
            inComment = (inComment && next != '\n') || next == '#'
        }
    }

    private fun consumeTerm(): TurtleToken.TermToken {
        source.expect('<')
        source.consume() // '<'
        val content = source.consumeAndDecodeWithoutWhitespaceUntil('>')
        source.consume() // '>'
        // valid non-relative terms start with `mailto:`, `http(s)://`, etc.
        return if (':' !in content) {
            TurtleToken.RelativeTerm(value = content)
        } else {
            TurtleToken.Term(value = content)
        }
    }

    private fun consumeLiteralTerm(terminator: Char): TurtleToken.TermToken {
        source.expect(terminator)
        source.consume() // terminator
        var escaped = false
        val value = source.consumeWhile { c -> (escaped || c != terminator).also { escaped = !escaped && c == '\\' } }
            .let { EscapeSequenceHelper.decodeNumericAndMappedCharacterEscapes(input = it) }
        source.consume() // terminator
        if (source.nextIs('@')) {
            source.consume()
            // language tag
            val language = consumeLanguageTag()
            return TurtleToken.LocalizedLiteralTerm(value, language)
        } else if (terminator == '"' && source.nextIs('^')) {
            source.consume() // '^'
            source.expect('^')
            source.consume() // '^'
            val type = next()
            source.expect(type is TurtleToken.NonLiteralTerm) { "Invalid literal type: $type" }
            return TurtleToken.LiteralTerm(value, type)
        } else {
            return TurtleToken.LiteralTerm(value, TurtleToken.Term(XSD.string.value))
        }
    }

    private fun consumeLongLiteralTerm(terminator: String): TurtleToken.TermToken {
        source.expect(matches(terminator)) { "`$terminator` sequence expected" }
        source.consume(terminator.length)
        var escaped = false
        val value = source.consumeWhile { c -> (escaped || !matches(terminator)).also { escaped = !escaped && c == '\\' } }
            .let { EscapeSequenceHelper.decodeNumericAndMappedCharacterEscapes(input = it) }
        source.consume(terminator.length)
        return if (source.nextIs('@')) {
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
        var nextCode = source.peek()
        while (nextCode != -1) {
            val next = Char(nextCode)
            if (!next.isDigit() && next != '.' && next.lowercaseChar() != 'e' && next != '+' && next != '-') {
                break
            }
            result.append(next)
            source.consume()
            nextCode = source.peek()
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
        val prefix = source.consumeWhile { it.isWhitespace() || it != ':' }
        if (source.nextIsEofOr { it.isWhitespace() }) {
            throw IllegalStateException("Invalid term: $prefix")
        }
        source.consume() // ':'
        val value = if (prefix == "_") consumeBlankName() else consumePrefixLocalName()
        return TurtleToken.PrefixedTerm(prefix, value)
    }

    private inline fun consumeLanguageTag(): String {
        // structure: '@' [a-zA-Z]+ ('-' [a-zA-Z0-9]+)*
        // keeping track of whether a `-` has been discovered, and we're in the second half
        var secondHalf = false
        return source.consumeWhile { current ->
            if (current == '-' && !secondHalf) {
                // at least one character has to be available after this one
                source.expect(source.peek(1).let { it != -1 && Char(it).isLetterOrDigit() }) { "Letter or digit expected" }
                secondHalf = true
                true
            } else {
                current.isLetter() || secondHalf && current.isLetterOrDigit()
            }
        }
    }

    /**
     * Returns `true` when [text] matches with the current [source] position, terminated by a whitespace or EOF
     */
    private fun matches(text: String): Boolean {
        var i = 0
        while (i < text.length) {
            val code = source.peek(i)
            if (code == -1) {
                return false
            }
            if (text[i] != Char(code)) {
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
            val code = source.peek(i)
            if (code == -1) {
                return false
            }
            if (text[i] != Char(code)) {
                return false
            }
            ++i
        }
        val code = source.peek(i)
        if (code == -1) {
            return false
        }
        val c = Char(code)
        return c.isWhitespace() || c == '<' || c == ':'
    }

    /**
     * Returns `true` when [text] matches with the current [source] position, terminated by a whitespace or EOF
     */
    private fun matchesKeywordIgnoreCase(text: String): Boolean {
        var i = 0
        while (i < text.length) {
            val code = source.peek(i)
            if (code == -1) {
                return false
            }
            val c = Char(code)
            if (!text[i].equals(c, ignoreCase = true)) {
                return false
            }
            ++i
        }
        val code = source.peek(i)
        if (code == -1) {
            return false
        }
        val c = Char(code)
        return c.isWhitespace() || c == '<' || c == ':'
    }

    /**
     * Consumes, and returns, the prefix local name value, respecting escaping rules.
     * Examples:
     *  * `ex:my\,triple` returns "my,triple"
     */
    private fun consumePrefixLocalName(): String {
        var c = source.peekOrBail()

        fun Char.isTerminatingCharacter(): Boolean =
            this.isWhitespace() ||
            this == ',' ||
            this == ';' ||
            this == '#' ||
            this == '<' ||
            this == '"' ||
            this == '\''

        val result = StringBuilder()
        var escaped = false

        while (escaped || !c.isTerminatingCharacter()) {
            val isReserved = c in ReservedCharacters
            if (escaped) {
                source.expect(isReserved) { "Invalid character `$c` encountered - unexpected escape!" }
                result.append(c)
                escaped = false
                source.consume()
                c = source.peekOrBail()
            } else /* !escaped */ {
                if (c == '%' && source.peek(1).isHexDecimal() && source.peek(2).isHexDecimal()) {
                    result.append(c)
                    // we know `peek()` won't yield `-1` here as the `isHexDecimal` check passed
                    result.append(Char(source.peek(1)))
                    result.append(Char(source.peek(2)))
                    source.consume(3)
                    c = source.peekOrBail()
                } else if (c == '.' && !source.peek(1).let { it != -1 && Char(it).isWhitespace() }) {
                    result.append('.')
                    result.append(source.peek(1))
                    source.consume(2)
                    c = source.peekOrBail()
                } else if (c == '\\') {
                    escaped = true
                    source.consume()
                    c = source.peekOrBail()
                } else if (TurtleToken.Structural[c] == null) {
                    result.append(c)
                    source.consume()
                    c = source.peekOrBail()
                } else {
                    // non-escaped structural character - leaving
                    break
                }
            }
        }
        return result.toString()
    }

    /**
     * Consumes, and returns, the blank name value, respecting escaping rules.
     * Examples:
     *  * `_:my\,triple` returns "my,triple"
     */
    private fun consumeBlankName(): String {
        var c = source.peekOrBail()

        fun Char.isTerminatingCharacter(): Boolean =
            this.isWhitespace() ||
                    this == ',' ||
                    this == ';' ||
                    // `:` is NOT allowed in blank names!
                    this == ':' ||
                    this == '#' ||
                    this == '<' ||
                    this == '"' ||
                    this == '\''

        val result = StringBuilder()
        var escaped = false

        while (escaped || !c.isTerminatingCharacter()) {
            val isReserved = c in ReservedCharacters
            if (escaped) {
                source.expect(isReserved) { "Invalid character `$c` encountered - unexpected escape!" }
                result.append(c)
                escaped = false
                source.consume()
                c = source.peekOrBail()
            } else /* !escaped */ {
                if (c == '%' && source.peek(1).isHexDecimal() && source.peek(2).isHexDecimal()) {
                    result.append(source.peek())
                    result.append(source.peek(1))
                    result.append(source.peek(2))
                    source.consume(3)
                    c = source.peekOrBail()
                } else if (c == '.' && !source.peek(1).let { it != -1 && (Char(it).isWhitespace() || it == '{'.code || it == '}'.code) }) {
                    result.append('.')
                    result.append(source.peek(1))
                    source.consume(2)
                    c = source.peekOrBail()
                } else if (c == '\\') {
                    escaped = true
                    source.consume()
                    c = source.peekOrBail()
                } else if (TurtleToken.Structural[c] == null) {
                    result.append(c)
                    source.consume()
                    c = source.peekOrBail()
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
