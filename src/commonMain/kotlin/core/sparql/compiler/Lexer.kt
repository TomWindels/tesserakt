package core.sparql.compiler

// later on, a buffered version can be made that makes "mini lexers" from the input string, reading line per line
//  and chains them together along to get the entire token sequence materialized
class Lexer internal constructor(private val src: String) {

    companion object {

        // convenience map for extracting tokens out of the string segments
        private val lut = Token.syntax
            .toList()
            .groupBy { (syntax) -> syntax.first() }
            .mapValues { (_, list) -> list.sortedByDescending { it.first.length } }

    }

    // range of the currently observed set of characters in the input string
    private var start = 0
    private var end = 0
    // extra helpers for debugging purposes
    private var lineIndex = 0
    private lateinit var current: Token

    /**
     * Converts the receiver string to an easier to interpret tokenized representation
     */
    fun tokenize(): Sequence<Token> = sequence {
        // resetting current indices
        start = 0
        end = 0
        // iterating over the input in segments using `nextSegment()`
        while (nextSegment()) {
            // parsing the next token syntax from the item until the end for the current segment has been reached
            while (start != end) {
                current = extractAnyTokenOrBail()
                start += current.syntax.length
                // actually yielding the resulting token
                yield(current)
            }
        }
    }

    /**
     * Returns a visual overview for the current line being processed and a line indicating the currently observed
     *  range
     */
    fun stacktrace(description: String): String {
        // finding the index of the last newline before `start`
        val newline = src.indexOfLast('\n', start - 1)
        val prefix = "$lineIndex "
        // if start equals end, token parsing went wrong, so last token information has to be used;
        // otherwise, last token can be highlighted
        val marker = if (start != end && start < src.length) {
            "^".repeat(end - start)
        } else {
            "^".repeat(current.syntax.length)
        }
        return if (newline == -1) buildString {
            append("$prefix| ${src.substringBefore('\n')}\n")
            append("${" ".repeat(prefix.length)}| ${" ".repeat(end - marker.length)}$marker - $description")
        } else buildString {
            append("$prefix| ${src.substringFromUntil(newline + 1, '\n')}\n")
            append("${" ".repeat(prefix.length)}| ${" ".repeat(end - newline - 1 - marker.length)}$marker - $description")
        }
    }

    /* helper methods */

    /**
     * Advances the current `start`, `end` range of observed characters for tokenization. Returns `false` if the end
     *  was reached
     */
    private inline fun nextSegment(): Boolean {
        while (true) {
            while (start < src.length && src[start].isWhitespace()) {
                if (src[start] == '\n') {
                    ++lineIndex
                }
                ++start
            }
            if (start == src.length) {
                // advancing end, so end is equal to start for more consistent behaviour
                end = start
                return false
            } else if (src[start] == '#') {
                // continuing until the end of the comment (EOL) has been reached
                while (start < src.length && src[start] != '\n') { ++start }
                // line end reached, incrementing line index
                ++lineIndex
                // going back to the top `while (true)`
            } else {
                end = start
                // continuing until the next whitespace
                while (end < src.length && !src[end].isWhitespace()) { ++end }
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
        lut[src[start]]?.forEach { (syntax, token) ->
            if (src.startsWith(syntax, start)) {
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
        return if (src[start] == '<') {
            val terminator = src.indexOf('>', startIndex = start, endIndex = end)
            if (terminator == -1) {
                bail("Term started at index $start is not properly terminated, `>` expected")
            }
            // + 1 as the `>` is part of this pattern element
            Token.Term(src.substring(start, terminator + 1))
        } else if (src.has(':')) {
            // two types of pattern elements possible: `prefix:` declarations and `prefix:name` pattern elements
            // remainder of the string should be valid, as only spaces or < ends a pattern element using a prefix
            val terminator = src.indexOf('<', startIndex = start, endIndex = end)
            if (terminator == -1) {
                Token.Term(src.substring(start, end))
            } else {
                Token.Term(src.substring(start, terminator))
            }
        } else if (src[start] == '?') {
            // remainder of the string should be valid, as only spaces or < ends a binding name
            val terminator = src.indexOf('<', startIndex = start, endIndex = end)
            if (terminator == -1) {
                Token.Term(src.substring(start, end))
            } else {
                Token.Term(src.substring(start, terminator))
            }
        } else if (src[start] == 'a' && (start == end - 1 || src[start + 1] == '<')) {
            // have to submit an 'a' manually, as the length of this token is used to advance the input
            Token.Term("a")
        } else {
            bail("Unrecognized token `${src.substring(start, end)}`")
        }
    }

    private fun bail(reason: String = "Internal compiler error"): Nothing {
        throw SyntaxError(problem = "Syntax error at index $start", description = reason)
    }

    private fun String.indexOf(char: Char, startIndex: Int, endIndex: Int): Int {
        var i = startIndex
        while (i < endIndex) {
            if (this[i] == char) {
                return i
            }
            ++i
        }
        return -1
    }

    private fun String.indexOfLast(char: Char, startIndex: Int, endIndex: Int = 0): Int {
        var i = startIndex
        while (i > endIndex - 1) {
            if (this[i] == char) {
                return i
            }
            --i
        }
        return -1
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

    private fun String.substringFromUntil(index: Int, until: Char): String {
        val end = indexOf(until, startIndex = index)
        return if (end != -1) substring(index, end) else substring(index)
    }

}