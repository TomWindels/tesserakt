package dev.tesserakt.rdf.trig.serialization

import dev.tesserakt.rdf.serialization.common.Prefixes
import dev.tesserakt.rdf.types.Quad.Companion.asNamedTerm
import dev.tesserakt.util.addFront
import dev.tesserakt.util.fit
import dev.tesserakt.util.removeFirstElement
import kotlin.jvm.JvmInline


sealed class Formatter {

    internal abstract fun format(tokens: Iterator<TriGToken>): Iterator<String>

}

data object SimpleFormatter: Formatter() {
    override fun format(tokens: Iterator<TriGToken>) = iterator {
        if (!tokens.hasNext()) {
            return@iterator
        }
        yield(tokens.next().syntax)
        while (tokens.hasNext()) {
            yield(" ")
            yield(tokens.next().syntax)
        }
    }
}

data class PrettyFormatter(
    val prefixes: Prefixes,
    /**
     * The (group of) character(s) to repeat for every depth in the resulting structure, typically either
     *  a set of spaces or tabs
     */
    // if we have no prefixes configured, the individual text elements are likely to be lengthy, causing a dynamic
    //  indent to be too long
    val indent: Indent = if (prefixes.isEmpty()) FixedStepIndent(INDENT_PATTERN) else DynamicIndent(INDENT_PATTERN),
    /**
     * The strategy used to flatten block structures
     */
    val flattenStrategy: FlattenStrategy = LengthBasedFlattening(64),
): Formatter() {

    /**
     * A small token buffer, buffering two values of the underlying token iterator source
     */
    private inner class TokenBuffer(private val iterator: Iterator<TriGToken>) {

        var current: TriGToken = iterator.next().mapped(prefixes)
            private set

        var next: TriGToken? = if (iterator.hasNext()) iterator.next().mapped(prefixes) else null
            private set

        private val buf = mutableListOf<TriGToken>()

        /**
         * A list of upcoming tokens, starting from [next], containing all following tokens matching those peeked in
         *  [peekUntil] (including the one not satisfying the predicate)
         *
         * IMPORTANT: this can include more non-matching tokens, if [peekUntil] is used more frequently than [advance]
         */
        val upcoming: List<TriGToken>
            // if next == null, the buf should be guaranteed empty
            get() = next?.let { next -> buf.addFront(next) } ?: emptyList()

        /**
         * Advances [current] and [next] read tokens, returning `true` if [current] has been updated, or false if the
         *  end has been reached
         */
        fun advance(): Boolean {
            current = next ?: return false
            next = when {
                buf.isNotEmpty() -> buf.removeFirstElement()
                iterator.hasNext() -> iterator.next().mapped(prefixes)
                else -> null
            }
            return true
        }

        /**
         * Consumes tokens for "lookahead" use until [predicate] returns `false` or no more tokens are available. These
         *  tokens are stored for future use through [current], [next] and [advance], including the one resulting
         *  in `false`.
         */
        inline fun peekUntil(predicate: (TriGToken) -> Boolean) {
            val next = next ?: /* end was reached */ return
            if (!predicate(next)) {
                // first one already doesn't match, not consuming any further
                return
            }
            while (iterator.hasNext()) {
                val current = iterator.next().mapped(prefixes)
                buf.add(current)
                if (!predicate(current)) {
                    break
                }
            }
        }

        override fun toString(): String {
            return "TokenBuffer { current: $current, next: $next, buf: $buf }"
        }

        private fun TriGToken.mapped(prefixes: Prefixes): TriGToken = when (this) {
            is TriGToken.Term -> prefixes
                .format(value.asNamedTerm())
                ?.let { TriGToken.PrefixedTerm(prefix = it.prefix, value = it.value) }
                ?: this

            is TriGToken.LiteralTerm -> {
                if (type is TriGToken.Term) {
                    prefixes
                        .format(type.value.asNamedTerm())
                        ?.let { TriGToken.LiteralTerm(value = value, type = TriGToken.PrefixedTerm(prefix = it.prefix, value = it.value)) }
                        ?: this
                } else {
                    this
                }
            }

            else -> this
        }

    }

    fun interface Indent {
        fun create(stack: Stack): String
    }

    /**
     * A fixed indent: the indent for new lines is fixed in length, depending on the depth of the preceding
     *  content (i.e., a single occurrence when in a graph, two occurrences when in a graph and repeated subject, etc.)
     */
    @JvmInline
    value class FixedStepIndent(private val pattern: String): Indent {
        override fun create(stack: Stack) = pattern.repeat(stack.depth)
    }

    /**
     * A dynamic indent: the indent for new lines is depending on the length of the preceding content
     */
    @JvmInline
    value class DynamicIndent(private val pattern: String): Indent {
        override fun create(stack: Stack): String {
            val length =
                (stack.g?.syntax?.length?.plus(1) ?: 0) +
                (stack.s?.syntax?.length?.plus(1) ?: 0) +
                (stack.p?.syntax?.length?.plus(1) ?: 0)
            return pattern.fit(length)
        }
    }

    /**
     * A class dictating what should and should not be flattened in representation for inner structural blocks of formatted outputs.
     *  Note: blocks containing blocks themselves are never subject to this check
     */
    sealed class FlattenStrategy {
        internal abstract fun shouldBeFlattened(content: List<TriGToken>): Boolean
    }

    data object NoFlattening: FlattenStrategy() {
        override fun shouldBeFlattened(content: List<TriGToken>) = false
    }

    data class LengthBasedFlattening(private val maxLength: Int): FlattenStrategy() {
        override fun shouldBeFlattened(content: List<TriGToken>): Boolean {
            return content.sumOf { it.syntax.length } <= maxLength
        }
    }

    class Stack {

        /* stack state */

        internal var g: TriGToken? = null
            private set
        internal var s: TriGToken? = null
            private set
        internal var p: TriGToken? = null
            private set
        // we don't have to track the object here

        val depth: Int
            get() {
                // depth inside the potential graph body
                val body = if (s == null) 1 else if (p == null) 2 else 3
                // if we're not actually inside a graph body (= default graph), we have to decrease one depth
                return if (g == null) body - 1 else body
            }

        internal fun setGraph(graph: TriGToken?) {
            g = graph
            s = null
            p = null
        }

        internal fun clearGraph() {
            g = null
            s = null
            p = null
        }

        internal fun setSubject(token: TriGToken) {
            s = token
            p = null
        }

        internal fun clearSubject() {
            s = null
            p = null
        }

        internal fun setPredicate(token: TriGToken) {
            checkNotNull(s)
            p = token
        }

        internal fun clearPredicate() {
            checkNotNull(s)
            p = null
        }

        override fun toString() = "Stack, positioned @ $g, $s, $p"

    }

    override fun format(tokens: Iterator<TriGToken>) = iterator {
        // first writing all prefixes
        prefixes.forEach { (base, uri) ->
            yield(TriGToken.Structural.PrefixAnnotationA.syntax)
            yield(" ")
            yield(base)
            yield(":")
            yield(" ")
            yield("<")
            yield(uri)
            yield(">")
            yield(" ")
            yield(TriGToken.Structural.StatementTermination.syntax)
            yield("\n")
        }
        if (prefixes.isNotEmpty()) {
            yield("\n")
        }
        val stack = Stack()
        val buffer = TokenBuffer(tokens)
        while (buffer.next != null) {
            when (buffer.next) {
                TriGToken.Structural.GraphStatementStart -> {
                    yieldAll(formatGraph(stack, buffer))
                }
                else -> {
                    yieldAll(processStatement(stack = stack, buffer = buffer, multiline = true))
                }
            }
            if (buffer.next != null) {
                yield("\n\n")
            }
        }
    }

    private fun processStatement(
        stack: Stack,
        buffer: TokenBuffer,
        multiline: Boolean,
    ): Iterator<String> = iterator {
        // setting the subject
        stack.setSubject(buffer.current)
        yield(formatToken(buffer))
        yield(" ")
        // setting the predicate
        stack.setPredicate(buffer.current)
        yield(formatToken(buffer))
        yield(" ")
        // the rest of *this* statement can only be additional objects or predicates
        var pos = 2
        while (true) {
            if (pos == 1) {
                stack.setPredicate(buffer.current)
                yield(formatToken(buffer))
                yield(" ")
                ++pos
            }
            // object is guaranteed
            check(pos == 2) {
                "Stack corruption encountered! Current stack layout: $stack"
            }
            yield(formatToken(buffer))
            // TODO: add additional flag/change the multiline argument type to better configure when/how to
            //  inline multiple objects
            while (buffer.current == TriGToken.Structural.ObjectTermination) {
                yield(" ")
                yield(buffer.current.syntax) // ","
                yield(" ")
                buffer.advance()
                yield(formatToken(buffer))
            }
            when (buffer.current) {
                TriGToken.Structural.StatementTermination -> {
                    yield(" ")
                    yield(buffer.current.syntax)
                    stack.clearSubject()
                    buffer.advance()
                    break
                }
                TriGToken.Structural.PredicateTermination -> {
                    yield(" ")
                    yield(buffer.current.syntax)
                    stack.clearPredicate()
                    pos = 1
                }
                else -> throw IllegalStateException("Unexpected token: ${buffer.current}")
            }
            if (!buffer.advance()) {
                // reached our own end
                return@iterator
            }
            if (buffer.current == TriGToken.Structural.GraphStatementEnd || buffer.current == TriGToken.Structural.BlankEnd) {
                // reached the block's end
                return@iterator
            }
            // preparing for next iteration
            if (multiline) {
                yield("\n")
                yield(indent.create(stack))
            } else {
                yield(" ")
            }
        }
    }

    private fun formatToken(buffer: TokenBuffer): String {
        when (buffer.current) {
            TriGToken.Structural.BlankStart -> {
                TODO("Implementation required, similar to statements list, terminating statements with `;` tokens")
            }
            is TriGToken.TermToken -> {
                val result = buffer.current.syntax
                buffer.advance()
                return result
            }
            TriGToken.Structural.TypePredicate -> {
                val result = buffer.current.syntax
                buffer.advance()
                return result
            }
            else -> {
                throw IllegalStateException("Unexpected buffer state: $buffer")
            }
        }
    }

    private fun formatGraph(stack: Stack, buffer: TokenBuffer) = iterator {
        require(buffer.current is TriGToken.TermToken)
        val g = buffer.current
        require(buffer.next == TriGToken.Structural.GraphStatementStart)
        yield(buffer.current.syntax)
        yield(" ")
        buffer.advance()
        // scanning ahead for the next tokens, deciding what formatting strategy should be applied
        // 1: taking all terms part of this block, until we start a new block or end this one
        buffer.peekUntil { token -> !token.isBlockToken() }
        val upcoming = buffer.upcoming
        // 2: scanning the tokens to ensure we don't create a new block inside this one
        val index = upcoming.indexOfFirst { it.isBlockToken() }
        val multiline =
            // the first block token should be the one ending this statement list, so we don't have any child block
            upcoming[index] != TriGToken.Structural.GraphStatementEnd ||
            // if we don't have a child block, we can ask the configured strategy what to do with this block layout
            !flattenStrategy.shouldBeFlattened(upcoming.take(index))
        // adjusting current statement
        yield(buffer.current.syntax) // "{"
        buffer.advance()
        // starting the next statements (yielding relevant tokens) inside a new frame
        stack.setGraph(g)
        if (multiline) {
            yield("\n")
            yield(indent.create(stack))
        } else {
            yield(" ")
        }
        yieldAll(processStatement(stack, buffer, multiline = multiline))
        while (buffer.current != TriGToken.Structural.GraphStatementEnd) {
            if (multiline) {
                yield("\n\n")
                yield(indent.create(stack))
            } else {
                yield(" ")
            }
            yieldAll(processStatement(stack, buffer, multiline = multiline))
        }
        if (buffer.current != TriGToken.Structural.GraphStatementEnd) {
            throw IllegalStateException("Did not reach the end of the graph statement properly!")
        }
        // terminating this frame
        stack.clearGraph()
        if (multiline) {
            yield("\n")
            yield(indent.create(stack))
        } else {
            yield(" ")
        }
        yield(buffer.current.syntax) // "}"
        buffer.advance()
    }

    /**
     * Returns true if this token denotes the start/end of a "block": `[`, `]`, `{` & `}`
     */
    private fun TriGToken.isBlockToken() =
        this == TriGToken.Structural.BlankStart ||
        this == TriGToken.Structural.BlankEnd ||
        this == TriGToken.Structural.GraphStatementStart ||
        this == TriGToken.Structural.GraphStatementEnd

}
