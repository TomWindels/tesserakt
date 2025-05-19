package dev.tesserakt.rdf.turtle.serialization

import dev.tesserakt.rdf.serialization.common.Prefixes
import dev.tesserakt.rdf.types.Quad.Companion.asNamedTerm
import dev.tesserakt.util.addFront
import dev.tesserakt.util.fit
import dev.tesserakt.util.removeFirstElement
import kotlin.jvm.JvmInline


sealed class Formatter {

    internal abstract fun format(tokens: Iterator<TurtleToken>): Iterator<String>

}

data object SimpleFormatter: Formatter() {
    override fun format(tokens: Iterator<TurtleToken>) = iterator {
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
): Formatter() {

    /**
     * A small token buffer, buffering two values of the underlying token iterator source
     */
    private inner class TokenBuffer(private val iterator: Iterator<TurtleToken>) {

        var current: TurtleToken = iterator.next().mapped(prefixes)
            private set

        var next: TurtleToken? = if (iterator.hasNext()) iterator.next().mapped(prefixes) else null
            private set

        private val buf = mutableListOf<TurtleToken>()

        /**
         * A list of upcoming tokens, starting from [next], containing all following tokens matching those peeked in
         *  [peekUntil] (including the one not satisfying the predicate)
         *
         * IMPORTANT: this can include more non-matching tokens, if [peekUntil] is used more frequently than [advance]
         */
        val upcoming: List<TurtleToken>
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
        inline fun peekUntil(predicate: (TurtleToken) -> Boolean) {
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

        private fun TurtleToken.mapped(prefixes: Prefixes): TurtleToken = when (this) {
            is TurtleToken.Term -> prefixes
                .format(value.asNamedTerm())
                ?.let { TurtleToken.PrefixedTerm(prefix = it.prefix, value = it.value) }
                ?: this

            is TurtleToken.LiteralTerm -> {
                if (type is TurtleToken.Term) {
                    prefixes
                        .format(type.value.asNamedTerm())
                        ?.let {
                            TurtleToken.LiteralTerm(
                                value = value,
                                type = TurtleToken.PrefixedTerm(prefix = it.prefix, value = it.value)
                            )
                        }
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
                (stack.s?.syntax?.length?.plus(1) ?: 0) +
                (stack.p?.syntax?.length?.plus(1) ?: 0)
            return pattern.fit(length)
        }
    }

    class Stack {

        /* stack state */

        internal var s: TurtleToken? = null
            private set
        internal var p: TurtleToken? = null
            private set
        // we don't have to track the object here

        val depth: Int
            get() = if (s == null) 0 else if (p == null) 1 else 2

        internal fun setSubject(token: TurtleToken) {
            s = token
            p = null
        }

        internal fun clearSubject() {
            s = null
            p = null
        }

        internal fun setPredicate(token: TurtleToken) {
            checkNotNull(s)
            p = token
        }

        internal fun clearPredicate() {
            checkNotNull(s)
            p = null
        }

        override fun toString() = "Stack, positioned @ $s, $p"

    }

    override fun format(tokens: Iterator<TurtleToken>) = iterator {
        // first writing all prefixes
        prefixes.forEach { (base, uri) ->
            yield(TurtleToken.Keyword.PrefixAnnotationA.syntax)
            yield(" ")
            yield(base)
            yield(":")
            yield(" ")
            yield("<")
            yield(uri)
            yield(">")
            yield(" ")
            yield(TurtleToken.Structural.StatementTermination.syntax)
            yield("\n")
        }
        if (prefixes.isNotEmpty()) {
            yield("\n")
        }
        val stack = Stack()
        val buffer = TokenBuffer(tokens)
        while (buffer.next != null) {
            yieldAll(processStatement(stack = stack, buffer = buffer, multiline = true))
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
            while (buffer.current == TurtleToken.Structural.ObjectTermination) {
                yield(" ")
                yield(buffer.current.syntax) // ","
                yield(" ")
                buffer.advance()
                yield(formatToken(buffer))
            }
            when (buffer.current) {
                TurtleToken.Structural.StatementTermination -> {
                    yield(" ")
                    yield(buffer.current.syntax)
                    stack.clearSubject()
                    buffer.advance()
                    break
                }
                TurtleToken.Structural.PredicateTermination -> {
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
            if (buffer.current == TurtleToken.Structural.BlankEnd) {
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
            TurtleToken.Structural.BlankStart -> {
                TODO("Implementation required, similar to statements list, terminating statements with `;` tokens")
            }
            is TurtleToken.TermToken -> {
                val result = buffer.current.syntax
                buffer.advance()
                return result
            }
            TurtleToken.Keyword.TypePredicate -> {
                val result = buffer.current.syntax
                buffer.advance()
                return result
            }
            else -> {
                throw IllegalStateException("Unexpected buffer state: $buffer")
            }
        }
    }

    /**
     * Returns true if this token denotes the start/end of a "block": `[`, `]`, `{` & `}`
     */
    private fun TurtleToken.isBlockToken() =
        this == TurtleToken.Structural.BlankStart ||
        this == TurtleToken.Structural.BlankEnd

}
