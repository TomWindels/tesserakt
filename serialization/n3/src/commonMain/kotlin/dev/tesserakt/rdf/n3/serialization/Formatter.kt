package dev.tesserakt.rdf.n3.serialization

import dev.tesserakt.util.addFront
import kotlin.jvm.JvmInline


sealed class Formatter {

    internal abstract fun format(tokens: Iterator<N3Token>): Iterator<String>

}

data object SimpleFormatter: Formatter() {
    override fun format(tokens: Iterator<N3Token>) = iterator {
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
    /**
     * The (group of) character(s) to repeat for every depth in the resulting structure, typically either
     *  a set of spaces or tabs
     */
    val indent: Indent = SimpleIndent("    "),
    /**
     * The strategy used to flatten block structures
     */
    val flattenStrategy: FlattenStrategy = LengthBasedFlattening(64),
): Formatter() {

    /**
     * A small token buffer, buffering two values of the underlying token iterator source
     */
    private class TokenBuffer(private val iterator: Iterator<N3Token>) {

        var current: N3Token = iterator.next()
            private set

        var next: N3Token? = if (iterator.hasNext()) iterator.next() else null
            private set

        private val buf = mutableListOf<N3Token>()

        /**
         * A list of upcoming tokens, starting from [next], containing all following tokens matching those peeked in
         *  [peekUntil] (including the one not satisfying the predicate)
         *
         * IMPORTANT: this can include more non-matching tokens, if [peekUntil] is used more frequently than [advance]
         */
        val upcoming: List<N3Token>
            // if next == null, the buf should be guaranteed empty
            get() = next?.let { next -> buf.addFront(next) } ?: emptyList()

        /**
         * Advances [current] and [next] read tokens, returning `true` if [current] has been updated, or false if the
         *  end has been reached
         */
        fun advance(): Boolean {
            current = next ?: return false
            next = when {
                buf.isNotEmpty() -> buf.removeFirst()
                iterator.hasNext() -> iterator.next()
                else -> null
            }
            return true
        }

        /**
         * Consumes tokens for "lookahead" use until [predicate] returns `false` or no more tokens are available. These
         *  tokens are stored for future use through [current], [next] and [advance], including the one resulting
         *  in `false`.
         */
        inline fun peekUntil(predicate: (N3Token) -> Boolean) {
            val next = next ?: /* end was reached */ return
            if (!predicate(next)) {
                // first one already doesn't match, not consuming any further
                return
            }
            while (iterator.hasNext()) {
                val current = iterator.next()
                buf.add(current)
                if (!predicate(current)) {
                    break
                }
            }
        }

        override fun toString(): String {
            return "TokenBuffer { current: $current, next: $next, buf: $buf }"
        }

    }

    fun interface Indent {
        fun create(depth: Int): String
    }

    @JvmInline
    value class SimpleIndent(private val pattern: String): Indent {
        override fun create(depth: Int) = pattern.repeat(depth)
    }

    /**
     * A class dictating what should and should not be flattened in representation for inner structural blocks of formatted outputs.
     *  Note: blocks containing blocks themselves are never subject to this check
     */
    sealed class FlattenStrategy {
        internal abstract fun shouldBeFlattened(content: List<N3Token>): Boolean
    }

    data object NoFlattening: FlattenStrategy() {
        override fun shouldBeFlattened(content: List<N3Token>) = false
    }

    data class LengthBasedFlattening(private val maxLength: Int): FlattenStrategy() {
        override fun shouldBeFlattened(content: List<N3Token>): Boolean {
            return content.sumOf { it.syntax.length } <= maxLength
        }
    }

    @JvmInline
    private value class Stack private constructor(private val frames: MutableList<Position>) {

        enum class Position {
            SUBJECT,
            PREDICATE,
            OBJECT
        }

        constructor(): this(mutableListOf(Position.SUBJECT))

        val indent: Int
            get() = frames.count { it != Position.SUBJECT } + frames.size - 1

        val position: Position
            get() = frames.last()

        fun set(target: Position) {
            frames[frames.size - 1] = target
        }

        fun advance() {
            when (frames[frames.size - 1]) {
                Position.SUBJECT -> frames[frames.size - 1] = Position.PREDICATE
                Position.PREDICATE -> frames[frames.size - 1] = Position.OBJECT
                Position.OBJECT -> throw IllegalStateException("Formatting error, encountered an invalid stack position!")
            }
        }

        fun startFrame() {
            frames.add(Position.SUBJECT)
        }

        fun stopFrame() {
            check(frames.size > 1) { "Invalid frame termination request! Root frame cannot be removed!" }
            frames.removeLast()
        }

        override fun toString() = frames.joinToString(" => ")

    }

    override fun format(tokens: Iterator<N3Token>) = processStatements(
        stack = Stack(),
        buffer = TokenBuffer(tokens),
        multiline = true
    )

    private fun processStatements(
        stack: Stack,
        buffer: TokenBuffer,
        multiline: Boolean,
    ): Iterator<String> = iterator {
        while (true) {
            if (stack.position == Stack.Position.SUBJECT) {
                yieldAll(formatToken(stack, buffer))
                yield(" ")
                stack.advance()
            }
            if (stack.position == Stack.Position.PREDICATE) {
                yieldAll(formatToken(stack, buffer))
                yield(" ")
                stack.advance()
            }
            // object is guaranteed
            check(stack.position == Stack.Position.OBJECT) {
                "Stack corruption encountered! Current stack layout: $stack"
            }
            yieldAll(formatToken(stack, buffer))
            when (buffer.current) {
                N3Token.Structural.StatementTermination -> {
                    yield(" ")
                    yield(buffer.current.syntax)
                    stack.set(Stack.Position.SUBJECT)
                }
                N3Token.Structural.PredicateTermination -> {
                    yield(" ")
                    yield(buffer.current.syntax)
                    stack.set(Stack.Position.PREDICATE)
                }
                N3Token.Structural.ObjectTermination -> {
                    yield(" ")
                    yield(buffer.current.syntax)
                    stack.set(Stack.Position.OBJECT)
                }
                else -> throw IllegalStateException("Unexpected token: ${buffer.current}")
            }
            if (!buffer.advance()) {
                // reached our own end
                return@iterator
            }
            if (buffer.current == N3Token.Structural.StatementsListEnd || buffer.current == N3Token.Structural.BlankEnd) {
                // reached the block's end
                return@iterator
            }
            // preparing for next iteration
            if (multiline) {
                yield("\n")
                yield(indent.create(stack.indent))
            } else {
                yield(" ")
            }
        }
    }

    private fun formatToken(stack: Stack, buffer: TokenBuffer) = iterator {
        when (buffer.current) {
            N3Token.Structural.BlankStart -> {
                TODO("Implementation required, similar to statements list, terminating statements with `;` tokens")
            }
            N3Token.Structural.StatementsListStart -> {
                yieldAll(formatStatementsList(stack, buffer))
            }
            is N3Token.TermToken -> {
                yield(buffer.current.syntax)
                buffer.advance()
            }
            else -> {
                throw IllegalStateException("Unexpected buffer state: $buffer")
            }
        }
    }

    private fun formatStatementsList(stack: Stack, buffer: TokenBuffer) = iterator {
        require(buffer.current == N3Token.Structural.StatementsListStart)
        // scanning ahead for the next tokens, deciding what formatting strategy should be applied
        // 1: taking all terms part of this block, until we start a new block or end this one
        buffer.peekUntil { token -> !token.isBlockToken() }
        val upcoming = buffer.upcoming
        // 2: scanning the tokens to ensure we don't create a new block inside this one
        val index = upcoming.indexOfFirst { it.isBlockToken() }
        val multiline =
            // the first block token should be the one ending this statement list, so we don't have any child block
            upcoming[index] != N3Token.Structural.StatementsListEnd ||
            // if we don't have a child block, we can ask the configured strategy what to do with this block layout
            !flattenStrategy.shouldBeFlattened(upcoming.take(index))
        // adjusting current statement
        yield(buffer.current.syntax) // "{"
        buffer.advance()
        // starting the next statements (yielding relevant tokens) inside a new frame
        stack.startFrame()
        if (multiline) {
            yield("\n")
            yield(indent.create(stack.indent))
        } else {
            yield(" ")
        }
        yieldAll(processStatements(stack, buffer, multiline = multiline))
        if (buffer.current != N3Token.Structural.StatementsListEnd) {
            throw IllegalStateException("Did not reach the end of the statements list properly!")
        }
        // terminating this frame
        stack.stopFrame()
        if (multiline) {
            yield("\n")
            yield(indent.create(stack.indent))
        } else {
            yield(" ")
        }
        yield(buffer.current.syntax) // "}"
        check(buffer.advance())
    }

    /**
     * Returns true if this token denotes the start/end of a "block": `[`, `]`, `{` & `}`
     */
    private fun N3Token.isBlockToken() =
        this == N3Token.Structural.BlankStart ||
        this == N3Token.Structural.BlankEnd ||
        this == N3Token.Structural.StatementsListStart ||
        this == N3Token.Structural.StatementsListEnd

}
