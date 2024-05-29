package dev.tesserakt.rdf.serialization

import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.rdf.types.Quad.Companion.asLiteralTerm
import dev.tesserakt.rdf.types.Store
import kotlin.jvm.JvmInline

object Turtle {

    fun String.parseTurtleString(): Store {
        return Parser(this).toStore()
    }

    class Parser(private val input: String) {

        private sealed interface Token {
            val syntax: String
            /** end object **/
            data object EOF: Token {
                override val syntax = "EOF"
            }
            /** fixed structural token types **/
            enum class StructuralToken(override val syntax: String): Token {
                StatementTermination("."),
                PredicateTermination(";"),
                ObjectTermination(","),
                PrefixAnnotation("@prefix"),
                /* end of structural tokens */;
                override fun toString() = "structural token `$syntax`"
                companion object {
                    val tokens = entries
                        .groupBy { it.syntax.first() }
                        .mapValues { it.value.sortedByDescending { it.syntax.length } }
                    val chars = tokens.keys.toCharArray()
                }
            }
            /** = `<my_term>`, value is without < > **/
            data class Term(val value: String): Token {
                override val syntax = "<$value>"
                override fun toString() = "term `$syntax`"
            }
            /** = `my:term` **/
            data class PrefixedTerm(val prefix: String, val value: String): Token {
                override val syntax = "$prefix:$value"
                override fun toString(): String = "term `$syntax`"
            }
            /** any literal **/
            @JvmInline
            value class LiteralTerm<T>(val value: T): Token {
                override val syntax get() = value.toString()
                override fun toString(): String = "literal `$syntax`"
            }
        }

        // IMPORTANT: this is not a valid state for parsing! Ideally, `reset()` followed by `parsePrefixes` should
        //  be called first (see `toStore()`)
        private var start = 0
        private var end = 0
        private var current: Token = Token.EOF

        private fun reset() {
            start = 0
            // has been init to 0 first, now using `nextStart()`
            start = nextStart()
            // and with start configured, the first stop can be found
            end = nextEnd()
            // and getting the next token
            current = nextToken()
        }

        /**
         * Increments (if possible) the observed range
         */
        private fun increment() {
            start += current.syntax.length
            start = nextStart()
            end = nextEnd()
        }

        private fun next() {
            current = nextToken()
        }

        /**
         * Returns the token that can currently be retrieved in the observed range. IMPORTANT: typically `increment()`
         *  would have to be called first
         */
        private fun nextToken(): Token = if (start > end) {
            Token.EOF
        } else if (input[start] == '<') {
            Token.Term(value = input.substring(start + 1, findOrBail('>')))
        } else if (start == end && input[start] == ':') {
            // special case
            Token.PrefixedTerm(prefix = "", value = "")
        } else if (has(':')) {
            val colon = findOrEnd(':')
            // this term can end sooner than `end` through the use of structural tokens
            val terminator = nextImplicitTermTerminator()
            Token.PrefixedTerm(prefix = input.substring(start, colon), value = input.substring(colon + 1, terminator))
        } else if (input[start] == '"') {
            // TODO: support special literal types, e.g. "5.5"^xsd:double
            Token.LiteralTerm(input.substring(start, findOrBail('"')))
        } else if (input[start].isDigit()) {
            Token.LiteralTerm(input.substring(start, nextImplicitTermTerminator()).toInt())
        } else {
            Token.StructuralToken
                .tokens[input[start]]
                ?.firstOrNull {
                    it.syntax.regionMatches(
                        thisOffset = 0,
                        other = input,
                        otherOffset = start,
                        length = end - start + 1,
                        ignoreCase = true
                    )
                }
                ?: bailOnBadToken()
        }

        private fun nextStart(): Int {
            var current = start
            while (current < input.length && input[current].isWhitespace()) {
                ++current
            }
            return current
        }

        private fun nextEnd(): Int {
            var current = start
            while (current < input.length && !input[current].isWhitespace()) {
                ++current
            }
            return current - 1
        }

        private fun has(char: Char): Boolean {
            var i = start
            while (i <= end) {
                if (input[i] == char) {
                    return true
                }
                ++i
            }
            return false
        }

        private fun findOrEnd(char: Char): Int {
            var i = start
            while (i <= end) {
                if (input[i] == char) {
                    return i
                }
                ++i
            }
            return end
        }

        private fun findOrBail(char: Char): Int {
            var i = start
            while (i <= end) {
                if (input[i] == char) {
                    return i
                }
                ++i
            }
            bailOnBadToken()
        }

        private fun nextImplicitTermTerminator(): Int {
            var i = start
            while (i <= end) {
                val c = input[i]
                if (c in Token.StructuralToken.chars) {
                    return i
                }
                ++i
            }
            return end + 1
        }

        private fun generateStackStrace(msg: String): String {
            val lineStart = input.lastIndexOf('\n', startIndex = start) + 1
            val lineEnd = input.indexOf('\n', startIndex = lineStart).let { if (it == -1) input.length else it }
            val line = input.substring(lineStart, lineEnd)
            val indicator = " ".repeat(start - lineStart) + "^".repeat(end - start + 1)
            return "$line\n$indicator - $msg"
        }

        private fun bailOnBadToken(): Nothing {
            val msg =
                if (start > end) "Reached EOF too early, last token was $current"
                else "Invalid token `${input.substring(start, end + 1)}`"
            val stacktrace = generateStackStrace(msg = msg)
            throw IllegalArgumentException("Turtle parsing failed at index ${start + 1}\n$stacktrace")
        }

        private fun bailOnBadStructure(msg: String = "Unexpected $current"): Nothing {
            val stacktrace = generateStackStrace(msg)
            throw IllegalArgumentException("Turtle parsing failed at index ${start + 1}\n$stacktrace")
        }

        private fun parsePrefixes(): Map<String, String> {
            val result = mutableMapOf<String, String>()
            while (current == Token.StructuralToken.PrefixAnnotation) {
                increment()
                next()
                val name = current as? Token.PrefixedTerm ?: bailOnBadToken()
                if (name.value.isNotBlank()) {
                    bailOnBadToken()
                }
                increment()
                next()
                val term = current as? Token.Term ?: bailOnBadToken()
                require(name.prefix !in result)
                result[name.prefix] = term.value
                increment()
                next()
                if (current != Token.StructuralToken.StatementTermination) {
                    bailOnBadStructure("Expected `.`")
                }
                increment()
                next()
            }
            return result
        }

        private inline fun parseTriples(prefixes: Map<String, String>, action: (Quad) -> Unit) {
            while (current != Token.EOF) {
                parseNewStatements(prefixes = prefixes, action = action)
            }
        }

        private inline fun parseNewStatements(prefixes: Map<String, String>, action: (Quad) -> Unit) {
            val subject = when (val c = current) {
                is Token.PrefixedTerm -> c.resolveOrBail(prefixes = prefixes).toQuadTerm()
                is Token.Term -> c.toQuadTerm()
                is Token.LiteralTerm<*> -> c.toQuadTerm()
                Token.EOF -> bailOnBadStructure()
                is Token.StructuralToken -> bailOnBadStructure()
            }
            increment()
            next()
            parseAfterSubject(prefixes = prefixes) { p, o ->
                action(Quad(s = subject, p = p, o = o))
            }
            // possibly looking at a `.` now; so consuming it if found
            if (current == Token.StructuralToken.StatementTermination) {
                increment()
                next()
            }
        }

        private inline fun parseAfterSubject(prefixes: Map<String, String>, action: (Quad.NamedTerm, Quad.Term) -> Unit) {
            while (true) {
                val p = when (val c = current) {
                    is Token.PrefixedTerm -> c.resolveOrBail(prefixes = prefixes).toQuadTerm()
                    is Token.Term -> c.toQuadTerm()
                    is Token.LiteralTerm<*> -> bailOnBadStructure("Unexpected literal as predicate")
                    Token.EOF -> bailOnBadStructure()
                    is Token.StructuralToken -> bailOnBadStructure()
                }
                increment()
                next()
                parseAfterPredicate(prefixes = prefixes) { o -> action(p, o) }
                if (current == Token.StructuralToken.PredicateTermination) {
                    increment()
                    next()
                } else {
                    break
                }
            }
        }

        private inline fun parseAfterPredicate(prefixes: Map<String, String>, action: (Quad.Term) -> Unit) {
            while (true) {
                // TODO: cover `[ ]`, `( )` cases
                val o = when (val c = current) {
                    is Token.PrefixedTerm -> c.resolveOrBail(prefixes = prefixes).toQuadTerm()
                    is Token.LiteralTerm<*> -> c.toQuadTerm()
                    is Token.Term -> c.toQuadTerm()
                    Token.EOF -> bailOnBadStructure()
                    is Token.StructuralToken -> bailOnBadStructure()
                }
                increment()
                next()
                action(o)
                if (current == Token.StructuralToken.ObjectTermination) {
                    increment()
                    next()
                } else {
                    break
                }
            }
        }

        private fun Token.PrefixedTerm.resolveOrBail(prefixes: Map<String, String>): Token.Term {
            val resolved = prefixes[prefix] ?: bailOnBadStructure(msg = "Unknown prefix: `$prefix`")
            return Token.Term(value = "$resolved$value")
        }

        private fun Token.Term.toQuadTerm() = Quad.NamedTerm(value = value)
        private fun Token.LiteralTerm<*>.toQuadTerm() = value.asLiteralTerm()

        fun toStore(): Store {
            reset()
            val prefixes = parsePrefixes()
            val result = Store()
            parseTriples(prefixes) {
                result.add(it)
            }
            return result
        }

    }

}
