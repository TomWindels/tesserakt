package dev.tesserakt.rdf.serialization

import dev.tesserakt.rdf.ontology.RDF
import dev.tesserakt.rdf.ontology.XSD
import dev.tesserakt.rdf.serialization.util.BufferedString
import dev.tesserakt.rdf.serialization.util.wrapAsBufferedReader
import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.rdf.types.Quad.Companion.asLiteralTerm
import dev.tesserakt.rdf.types.Store
import kotlin.jvm.JvmInline

object Turtle {

    fun String.parseTurtleString(): Store {
        return Parser(BufferedString(wrapAsBufferedReader())).toStore()
    }

    internal fun BufferedString.parseTurtleString(): Store {
        return Parser(this).toStore()
    }

    class Parser internal constructor(private val input: BufferedString) {

        private sealed interface Token {
            val syntax: String
            val syntaxLength: Int get() = syntax.length
            /** end object **/
            data object EOF: Token {
                override val syntax = "EOF"
            }
            /** fixed structural token types **/
            enum class StructuralToken(override val syntax: String): Token {
                StatementTermination("."),
                PredicateTermination(";"),
                ObjectTermination(","),
                BaseAnnotationA("@base"),
                BaseAnnotationB("BASE"),
                PrefixAnnotationA("@prefix"),
                PrefixAnnotationB("PREFIX"),
                TypePredicate("a"),
                TrueLiteral("true"),
                FalseLiteral("false"),
                /* end of structural tokens */;
                override fun toString() = "structural token `$syntax`"
                companion object {
                    val tokens = entries
                        .groupBy { it.syntax.first() }
                        .mapValues { it.value.sortedByDescending { it.syntax.length } }
                }
            }
            /** = `<my_term>`, value is without < > **/
            data class Term(val value: String): Token {
                override val syntax = "<$value>"
                override fun toString() = "term `$syntax`"
            }
            /** = `#term` **/
            @JvmInline
            value class RelativeTerm(val value: String): Token {
                override val syntax get() = "<$value>"
                override fun toString(): String = "term `$syntax`"
            }
            /** = `my:term` **/
            data class PrefixedTerm(val prefix: String, val value: String): Token {
                override val syntax = "$prefix:$value"
                override fun toString(): String = "term `$syntax`"
            }
            /** any literal **/
            class LiteralTerm<T>(
                val value: T,
                val dataType: Quad.NamedTerm,
                override val syntaxLength: Int
            ): Token {
                override val syntax get() = value.toString()
                override fun toString(): String = "literal `$syntax`"
            }
        }

        private var length = 0
        private var current: Token = Token.EOF
        private var base: Token.Term? = null
        private var prefixes: Map<String, String> = emptyMap()

        init {
            // continuing until first relevant token
            advance()
            // and finding its length
            length = length()
            // and getting the next token
            next()
            // now getting the prefixes etc. setup
            parseStart()
        }

        /**
         * Increments (if possible) the observed range
         */
        private fun increment() {
            input.consume(current.syntaxLength)
            advance()
            length = length()
        }

        private fun next() {
            current = nextToken()
        }

        /**
         * Returns the token that can currently be retrieved in the observed range. IMPORTANT: typically `increment()`
         *  would have to be called first
         */
        private fun nextToken(): Token = if (input.peek() == null) {
            Token.EOF
        } else if (input.peek() == '<') {
            if (input.peek(1) == '#') {
                Token.RelativeTerm(value = input.substring(1, findNextOrBail('>')))
            } else {
                Token.Term(value = input.substring(1, findNextOrBail('>')))
            }
        } else if (length == 0 && input.peek() == ':') {
            // special case
            Token.PrefixedTerm(prefix = "", value = "")
        } else if (input.peek() == '"') {
            val end = findNextOrBail('"')
            val value = input.substring(1, end)
            val dataType: Quad.NamedTerm
            val additionalOffset: Int
            when {
                input.peek(end + 1) == '@' -> {
                    val tag = input.substring(end + 1, findNextWhitespaceOrBail(start = end + 1))
                    additionalOffset = tag.length + 1 // + "@"
                    dataType = RDF.langString
                }
                input.peek(end + 1) == '^' && input.peek(end + 2) == '^' -> {
                    input.consume(end + 3)
                    val datatype = nextToken()
                    additionalOffset = datatype.syntaxLength + 2 - end - 3 // plus "^^" minus what was consumed above
                    dataType = datatype.asNamedTermOrBail()
                }
                else -> {
                    additionalOffset = 0
                    dataType = XSD.string
                }
            }
            when (dataType) {
                XSD.int -> Token.LiteralTerm(
                    value = value.toInt(),
                    syntaxLength = end + 1 + additionalOffset,
                    dataType = dataType
                )
                XSD.long -> Token.LiteralTerm(
                    value = value.toLong(),
                    syntaxLength = end + 1 + additionalOffset,
                    dataType = dataType
                )
                XSD.float -> Token.LiteralTerm(
                    value = value.toFloat(),
                    syntaxLength = end + 1 + additionalOffset,
                    dataType = dataType
                )
                XSD.double -> Token.LiteralTerm(
                    value = value.toDouble(),
                    syntaxLength = end + 1 + additionalOffset,
                    dataType = dataType
                )
                XSD.boolean -> Token.LiteralTerm(
                    value = value.toBooleanStrict(),
                    syntaxLength = end + 1 + additionalOffset,
                    dataType = dataType
                )
                else -> Token.LiteralTerm(
                    value = value,
                    syntaxLength = end + 1 + additionalOffset,
                    dataType = dataType
                )
            }
        } else if (input.peek()!!.isDigit()) {
            val end = nextImplicitTermTerminator()
            Token.LiteralTerm(
                value = input.substring(0, end).toInt(),
                syntaxLength = end,
                dataType = XSD.int
            )
        } else if (has(':')) {
            val colon = findOrEnd(':')
            // this term can end sooner than `end` through the use of structural tokens
            Token.PrefixedTerm(
                prefix = input.substring(0, colon),
                value = input.substring(colon + 1, nextImplicitTermTerminator(colon + 1))
            )
        } else {
            Token.StructuralToken
                .tokens[input.peek()!!]
                ?.firstOrNull {
                    input.startsWith(
                        other = it.syntax,
                        ignoreCase = true
                    )
                }
                ?: bailOnBadToken()
        }

        private fun advance() {
            var c = input.peek()
            while (true) {
                when {
                    c == null -> break
                    c == '#' -> while (c != null && c != '\n') {
                        input.consume()
                        c = input.peek()
                    }
                    !c.isWhitespace() -> break
                }
                input.consume()
                c = input.peek()
            }
            if (c == null) {
                current = Token.EOF
            }
        }

        private fun length(): Int {
            var current = 0
            var canStop = true
            var c = input.peek(current)
            while (c != null && (!c.isWhitespace() || !canStop)) {
                if (c == '"') {
                    canStop = !canStop
                }
                ++current
                c = input.peek(current)
            }
            if (c == null) {
                return 0
            }
            return current
        }

        private fun has(char: Char): Boolean {
            var i = 0
            while (i <= length) {
                if (input.peek(i) == char) {
                    return true
                }
                ++i
            }
            return false
        }

        private fun findOrEnd(char: Char): Int {
            var i = 0
            while (i <= length) {
                if (input.peek(i) == char) {
                    return i
                }
                ++i
            }
            return length
        }

        private fun findNextOrBail(char: Char, start: Int = 1): Int {
            var i = start
            while (i <= length) {
                if (input.peek(i) == char) {
                    return i
                }
                ++i
            }
            bailOnBadToken()
        }

        private fun findNextWhitespaceOrBail(start: Int = 1): Int {
            var i = start
            while (i <= length) {
                if (input.peek(i)!!.isWhitespace()) {
                    return i
                }
                ++i
            }
            bailOnBadToken()
        }

        private fun nextImplicitTermTerminator(start: Int = 0): Int {
            var i = start
            while (i <= length) {
                val c = input.peek(i) ?: bailOnBadStructure("Unexpected EOF")
                if (
                    c.isWhitespace() ||
                    c == '.' ||
                    c == ';' ||
                    c == ',' ||
                    c == '#'
                ) {
                    return i
                }
                ++i
            }
            return length + 1
        }

        private fun Token.asNamedTermOrBail(): Quad.NamedTerm = when (this) {
            is Token.PrefixedTerm -> resolveOrBail().toQuadTerm()
            is Token.RelativeTerm -> resolveOrBail().toQuadTerm()
            is Token.Term -> toQuadTerm()
            Token.EOF,
            is Token.StructuralToken,
            is Token.LiteralTerm<*> -> bailOnBadStructure("Expected a named term, got $this")
        }

        private fun generateStackStrace(msg: String): String {
            return input.report(indicator = "^".repeat(length), message = msg)
        }

        private fun bailOnBadToken(): Nothing {
            val msg =
                if (input.peek() == null) "Reached EOF too early, last token was $current"
                else "Invalid token `${input.substring(0, length + 1)}`"
            val stacktrace = generateStackStrace(msg = msg)
            throw IllegalArgumentException("Turtle parsing failed at index ${input.index() + 1} (last read $current)\n$stacktrace")
        }

        private fun bailOnBadStructure(msg: String = "Unexpected $current"): Nothing {
            val stacktrace = generateStackStrace(msg)
            throw IllegalArgumentException("Turtle parsing failed at index ${input.index() + 1} (last read $current)\n$stacktrace")
        }

        private fun parseStart() {
            if (current == Token.StructuralToken.BaseAnnotationA || current == Token.StructuralToken.BaseAnnotationB) {
                increment()
                next()
                val term = current as? Token.Term ?: bailOnBadToken()
                base = term
                increment()
                next()
            }
            prefixes = buildMap {
                while (
                    current == Token.StructuralToken.PrefixAnnotationA ||
                    current == Token.StructuralToken.PrefixAnnotationB
                ) {
                    increment()
                    next()
                    val name = current as? Token.PrefixedTerm ?: bailOnBadToken()
                    if (name.value.isNotBlank()) {
                        bailOnBadToken()
                    }
                    increment()
                    next()
                    val term = current as? Token.Term ?: bailOnBadToken()
                    require(name.prefix !in this)
                    put(name.prefix, term.value)
                    increment()
                    next()
                    if (current != Token.StructuralToken.StatementTermination) {
                        // if strict
//                        bailOnBadStructure("Expected `.`")
                    } else {
                        increment()
                        next()
                    }
                }
            }
        }

        private inline fun parseTriples(action: (Quad) -> Unit) {
            while (current != Token.EOF) {
                parseNewStatements(action = action)
            }
        }

        private inline fun parseNewStatements(action: (Quad) -> Unit) {
            val subject = when (val c = current) {
                is Token.RelativeTerm -> c.resolveOrBail().toQuadTerm()
                is Token.PrefixedTerm -> c.resolveOrBail().toQuadTerm()
                is Token.Term -> c.toQuadTerm()
                is Token.LiteralTerm<*> -> c.toQuadTerm()
                Token.EOF -> bailOnBadStructure()
                is Token.StructuralToken -> bailOnBadStructure()
            }
            increment()
            next()
            parseAfterSubject { p, o ->
                action(Quad(s = subject, p = p, o = o))
            }
            // possibly looking at a `.` now; so consuming it if found
            if (current == Token.StructuralToken.StatementTermination) {
                increment()
                next()
            }
        }

        private inline fun parseAfterSubject(action: (Quad.NamedTerm, Quad.Term) -> Unit) {
            while (true) {
                val p = when (val c = current) {
                    is Token.RelativeTerm -> c.resolveOrBail().toQuadTerm()
                    is Token.PrefixedTerm -> c.resolveOrBail().toQuadTerm()
                    is Token.Term -> c.toQuadTerm()
                    Token.StructuralToken.TypePredicate -> RDF.type
                    is Token.LiteralTerm<*> -> bailOnBadStructure("Unexpected literal as predicate")
                    Token.EOF -> bailOnBadStructure()
                    is Token.StructuralToken -> bailOnBadStructure()
                }
                increment()
                next()
                parseAfterPredicate { o -> action(p, o) }
                if (current == Token.StructuralToken.PredicateTermination) {
                    increment()
                    next()
                } else {
                    break
                }
            }
        }

        private inline fun parseAfterPredicate(action: (Quad.Term) -> Unit) {
            while (true) {
                // TODO: cover `[ ]`, `( )` cases
                val o = when (val c = current) {
                    is Token.RelativeTerm -> c.resolveOrBail().toQuadTerm()
                    is Token.PrefixedTerm -> c.resolveOrBail().toQuadTerm()
                    is Token.LiteralTerm<*> -> c.toQuadTerm()
                    is Token.Term -> c.toQuadTerm()
                    Token.StructuralToken.TrueLiteral -> true.asLiteralTerm()
                    Token.StructuralToken.FalseLiteral -> false.asLiteralTerm()
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

        private fun Token.RelativeTerm.resolveOrBail(): Token.Term {
            val b = base?.value ?: bailOnBadStructure(msg = "Relative IRI found without a BASE configured")
            return Token.Term(value = "$b$value")
        }

        private fun Token.PrefixedTerm.resolveOrBail(): Token.Term {
            val resolved = prefixes[prefix] ?: bailOnBadStructure(msg = "Unknown prefix: `$prefix`")
            return Token.Term(value = "$resolved$value")
        }

        private fun Token.Term.toQuadTerm() = Quad.NamedTerm(value = value)
        private fun Token.LiteralTerm<*>.toQuadTerm() = value.asLiteralTerm().copy(type = dataType)

        fun toStore(): Store {
            val result = Store()
            parseTriples {
                result.add(it)
            }
            return result
        }

        fun toList(): List<Quad> {
            return buildList {
                parseTriples {
                    add(it)
                }
            }
        }

    }

}
