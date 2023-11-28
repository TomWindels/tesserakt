package core.sparql.compiler.lexer

import core.sparql.compiler.types.Token

class StringLexer(private val input: String): Lexer() {

    // range of the currently observed set of characters in the input string
    private var start = 0
    private var end = 0
    // extra helpers for logging purposes
    private var lineIndex = 0
    private lateinit var current: Token

    override fun hasNext(): Boolean {
        return start < end || nextSegment()
    }

    override fun next(): Token {
        current = extractAnyTokenOrBail()
        start += current.syntax.length
        // `current` now points to the next token requested
        return current
    }

    override fun position() = start

    override fun stacktrace(message: String): String {
        // finding the index of the last newline before `start`
        val newline = input.indexOfLast('\n', start - 1)
        val prefix = "$lineIndex "
        // if start equals end, token parsing went wrong, so last token information has to be used;
        // otherwise, last token can be highlighted
        val marker = if (start != end && start < input.length) {
            "^".repeat(end - start)
        } else {
            "^".repeat(current.syntax.length)
        }
        return if (newline == -1) buildString {
            append("$prefix| ${input.substringBefore('\n')}\n")
            append("${" ".repeat(prefix.length)}| ${" ".repeat(end - marker.length)}$marker - $message")
        } else buildString {
            append("$prefix| ${input.substringFromUntil(newline + 1, '\n')}\n")
            append("${" ".repeat(prefix.length)}| ${" ".repeat(end - newline - 1 - marker.length)}$marker - $message")
        }
    }

    /* helper methods */

    /**
     * Advances the current `start`, `end` range of observed characters for tokenization. Returns `false` if the end
     *  was reached
     */
    private inline fun nextSegment(): Boolean {
        while (true) {
            while (start < input.length && input[start].isWhitespace()) {
                if (input[start] == '\n') {
                    ++lineIndex
                }
                ++start
            }
            if (start == input.length) {
                // advancing end, so end is equal to start for more consistent behaviour
                end = start
                return false
            } else if (input[start] == '#') {
                // continuing until the end of the comment (EOL) has been reached
                while (start < input.length && input[start] != '\n') { ++start }
                // line end reached, incrementing line index
                ++lineIndex
                // going back to the top `while (true)`
            } else {
                end = start
                // continuing until the next whitespace
                while (end < input.length && !input[end].isWhitespace()) { ++end }
                // allowing it to be used
                return true
            }
        }
    }

    /**
     * Attempts to find any token (syntax or pattern) starting at the given index. Bails if no token is starting
     *  at the given index (syntax error)
     */
    private inline fun extractAnyTokenOrBail(): Token {
        // first attempting to find a syntax type
        lut[input[start]]?.forEach { (syntax, token) ->
            if (input.startsWith(syntax, start)) {
                return token
            }
        }
        // falling back to pattern element
        return extractPatternElementOrBail()
    }

    private inline fun extractPatternElementOrBail(): Token {
        // the `<...>` & `?...` structures cannot be split apart using whitespace, so looking for these
        // `(...)` & `...|...` can be split apart using whitespace, and are hence part of the token syntax looked
        //  up before
        return if (input[start] == '<') {
            val terminator = input.indexOf('>', startIndex = start, endIndex = end)
            if (terminator == -1) {
                bail("Term started at index $start is not properly terminated, `>` expected")
            }
            // + 1 as the `>` is part of this pattern element
            Token.Term(input.substring(start, terminator + 1))
        } else if (input.has(':')) {
            // two types of pattern elements possible: `prefix:` declarations and `prefix:name` pattern elements
            // remainder of the string should be valid, as only spaces or < ends a pattern element using a prefix
            val terminator = input.indexOf('<', startIndex = start, endIndex = end)
            if (terminator == -1) {
                Token.Term(input.substring(start, end))
            } else {
                Token.Term(input.substring(start, terminator))
            }
        } else if (input[start] == '?') {
            // remainder of the string should be valid, as only spaces or < ends a binding name
            val terminator = input.indexOf('<', startIndex = start, endIndex = end)
            if (terminator == -1) {
                Token.Term(input.substring(start, end))
            } else {
                Token.Term(input.substring(start, terminator))
            }
        } else if (input[start] == 'a' && (start == end - 1 || input[start + 1] == '<')) {
            // have to submit an 'a' manually, as the length of this token is used to advance the input
            Token.Term("a")
        } else {
            bail("Unrecognized token `${input.substring(start, end)}`")
        }
    }

    private fun String.has(char: Char): Boolean {
        var i = start
        while (i < end) {
            if (this[i] == char) {
                return true
            }
            ++i
        }
        return false
    }

}
