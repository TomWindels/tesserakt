package dev.tesserakt.sparql.compiler.lexer

import dev.tesserakt.sparql.compiler.CompilerError

@Suppress("NOTHING_TO_INLINE")
class StringLexer(private val input: String): Lexer() {

    // range of the currently observed set of characters in the input string
    private var start = 0
    private var end = 0
    // extra helpers for logging purposes
    private var lineIndex = 0
    override lateinit var current: Token
        private set

    override fun advance() {
        current = if (start >= end && !nextSegment()) {
            Token.EOF
        } else {
            extractAnyTokenOrBail()
                .also { new -> start += new.syntax.length }
        }
    }

    override fun position() = start

    override fun stacktrace(type: CompilerError.Type, message: String): String {
        return when (type) {
            CompilerError.Type.SyntaxError -> syntaxErrorStacktrace(message = message)
            CompilerError.Type.StructuralError -> analyserStacktrace(message = message)
        }
    }

    /* helper methods */

    // version of the stacktrace using the current `start .. end` interval to highlight the problem
    private fun syntaxErrorStacktrace(message: String): String {
        // finding the index of the last newline before `start`
        val newline = input.indexOfLast('\n', startIndex = start - 1)
        val prefix = "$lineIndex "
        // if start equals end, token parsing went wrong, so last token information has to be used;
        // otherwise, last token can be highlighted
        val marker = "^".repeat(end - start)
        return if (newline == -1) buildString {
            append("$prefix| ${input.substringBefore('\n')}\n")
            append("${" ".repeat(prefix.length)}| ${" ".repeat(end - marker.length)}$marker - $message")
        } else buildString {
            append("$prefix| ${input.substringFromUntil(newline + 1, '\n')}\n")
            append("${" ".repeat(prefix.length)}| ${" ".repeat(end - newline - 1 - marker.length)}$marker - $message")
        }
    }

    // version of the stacktrace using the current `token` to highlight the problem
    private fun analyserStacktrace(message: String): String {
        // finding the index of the last newline before `start`
        val newline = input.indexOfLast('\n', startIndex = start - 1)
        val prefix = "$lineIndex "
        // if start equals end, token parsing went wrong, so last token information has to be used;
        // otherwise, last token can be highlighted
        val marker = "^".repeat(current.syntax.length)
        return if (newline == -1) buildString {
            append("$prefix| ${input.substringBefore('\n')}\n")
            append("${" ".repeat(prefix.length)}| ${" ".repeat(start - marker.length)}$marker - $message")
        } else buildString {
            append("$prefix| ${input.substringFromUntil(newline + 1, '\n')}\n")
            append("${" ".repeat(prefix.length)}| ${" ".repeat(start - newline - 1 - marker.length)}$marker - $message")
        }
    }

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
                // continuing until the next whitespace in a non-escaped context
                var canStop = true
                while (end < input.length && (!input[end].isWhitespace() || !canStop)) {
                    if (input[end] == '"' || input[end] == '\'') {
                        canStop = !canStop
                    }
                    ++end
                }
                // allowing it to be used
                return true
            }
        }
    }

    /**
     * Attempts to find any token (syntax or pattern) starting at the given index. Bails if no token is starting
     *  at the given index (syntax error)
     */
    // first attempting to find a pattern-like element, then falling back to a syntax type
    private inline fun extractAnyTokenOrBail(): Token =
         extractPatternElementOrBinding() ?: extractSyntacticToken()
        ?: bail("Unrecognized token `${input.substring(start, end)}`")

    private inline fun extractSyntacticToken(): Token? =
        lut[input[start].lowercaseChar()]
            ?.firstOrNull { (syntax, _) -> input.startsWith(syntax, start, ignoreCase = true) }
            ?.second

    private inline fun extractPatternElementOrBinding(): Token? {
        // the `<...>` & `?...` structures cannot be split apart using whitespace, so looking for these
        // `(...)` & `...|...` can be split apart using whitespace, and are hence part of the token syntax looked
        //  up before
        return if (input[start] == '<') {
            val terminator = input.indexOf('>', startIndex = start, endIndex = end)
            if (terminator == -1) {
                bail("Term started at index $start is not properly terminated, `>` expected")
            }
            // summing two extra to the start, same logic as `?...` bindings
            start += 2
            // temporarily minus 1 for the substring
            Token.Term(input.substring(start - 1, terminator))
        } else if (input[start] == ':' || input[start].isLetterOrDigit() && input.has(':')) {
            val colon = input.indexOf(':', startIndex = start, endIndex = end)
            // two types of pattern elements possible: `prefix:` declarations and `prefix:name` pattern elements
            // remainder of the string should be valid, as only spaces or < ends a pattern element using a prefix
            val terminator = input.endOfBindingOrPrefixedTerm()
            if (terminator == -1) {
                Token.PrefixedTerm(
                    namespace = input.substring(start, colon),
                    value = input.substring(colon + 1, end)
                )
            } else {
                Token.PrefixedTerm(
                    namespace = input.substring(start, colon),
                    value = input.substring(colon + 1, terminator)
                )
            }
        } else if (end - start > 1 && input[start] == '_' && input[start + 1] == ':') {
            val terminator = input.endOfBindingOrPrefixedTerm()
            if (terminator == -1) {
                Token.BlankTerm(
                    value = input.substring(start, end)
                )
            } else {
                Token.BlankTerm(
                    value = input.substring(start, terminator)
                )
            }
        } else if (input[start] == '?') {
            // remainder of the string should be valid, as only spaces or < ends a binding name
            // omitting the `?` in the front, so start + 1
            val terminator = input.endOfBindingOrPrefixedTerm()
            // manually advancing start, as we additionally consumed the `?`, but don't have this in the syntax length
            //  used to move `start` along
            ++start
            // the use of start below doesn't include the `?` anymore, but adding the binding's length to start
            //  will create the appropriate shifting behaviour
            if (terminator == -1) {
                Token.Binding(input.substring(start, end))
            } else {
                Token.Binding(input.substring(start, terminator))
            }
        } else if (input[start] == '"') {
            val terminator = input.indexOf('"', startIndex = start + 1, endIndex = end)
            if (terminator == -1) {
                bail("""Expected `"` at the end: `${input.substring(start, end)}`""")
            }
            Token.StringLiteral(input.substring(start + 1, terminator))
        } else if (input[start] == '\'') {
            val terminator = input.indexOf('\'', startIndex = start + 1, endIndex = end)
            if (terminator == -1) {
                bail("""Expected `'` at the end: `${input.substring(start, end)}`""")
            }
            Token.StringLiteral(input.substring(start + 1, terminator))
        } else if (input[start].representsNumber()) {
            var terminator = start + 1
            while (terminator < end && input[terminator].representsNumber()) { ++terminator }
            val substring = input.substring(start, terminator)
            return substring.toLongOrNull()?.let { Token.NumericLiteral(it) }
                ?: substring.toDoubleOrNull()?.let { Token.NumericLiteral(it) }
        } else {
            // nothing found
            null
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

    /**
     * Finds the end index (if any) in the currently considered character range terminating the binding / prefixed
     *  term. These two types of "string inputs" are terminated quicker than regular `<...>` terms
     */
    private fun String.endOfBindingOrPrefixedTerm() = indexOf(
        // a fair number of characters can terminate a binding / prefixed term
        '<', '?', '.', ';', ',', '{', '}', '(', ')', '|', '/', '*', '+', '[',
        startIndex = start + 1,
        endIndex = end
    )

    /**
     * Simply checks if the character can represent a digit (so either `isDigit()` or `.`)
     */
    private fun Char.representsNumber() = isDigit() || this == '.'

}
