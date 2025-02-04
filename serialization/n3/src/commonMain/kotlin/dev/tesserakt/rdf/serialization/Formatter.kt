package dev.tesserakt.rdf.serialization

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
): Formatter() {

    /**
     * A small token buffer, buffering two values of the underlying token iterator source
     */
    private class TokenBuffer(private val iterator: Iterator<N3Token>) {

        var current: N3Token = iterator.next()
            private set

        var next: N3Token? = if (iterator.hasNext()) iterator.next() else null
            private set

        /**
         * Advances [current] and [next] read tokens, returning `true` if [current] has been updated, or false if the
         *  end has been reached
         */
        fun advance(): Boolean {
            current = next ?: return false
            next = if (iterator.hasNext()) iterator.next() else null
            return true
        }

        override fun toString(): String {
            return "TokenBuffer { current: $current, next: $next }"
        }

    }

    fun interface Indent {
        fun create(depth: Int): String
    }

    @JvmInline
    value class SimpleIndent(private val pattern: String): Indent {
        override fun create(depth: Int) = pattern.repeat(depth)
    }

    @JvmInline
    private value class Stack private constructor(private val current: MutableList<Position>) {

        enum class Position {
            SUBJECT,
            PREDICATE,
            OBJECT
        }

        constructor(): this(mutableListOf(Position.SUBJECT))

        val indent: Int
            get() = when {
                current.isEmpty() -> 0
                current.size == 1 && current.last() == Position.SUBJECT -> 0
                current.size == 1 && current.last() != Position.SUBJECT -> 1
                current[current.size - 2] == Position.SUBJECT -> current.size - 1
                else -> current.size
            }

        val position: Position
            get() = current.last()

        fun set(target: Position) {
            current[current.size - 1] = target
        }

        fun advance() {
            when (current[current.size - 1]) {
                Position.SUBJECT -> current[current.size - 1] = Position.PREDICATE
                Position.PREDICATE -> current[current.size - 1] = Position.OBJECT
                Position.OBJECT -> throw IllegalStateException("Formatting error, encountered an invalid stack position!")
            }
        }

        fun startFrame() {
            current.add(Position.SUBJECT)
        }

        fun stopFrame() {
            current.removeLast()
        }

        override fun toString() = current.joinToString(" => ")

    }

    private fun processStatements(stack: Stack, buffer: TokenBuffer): Iterator<String> = iterator {
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
            yield("\n")
            yield(indent.create(stack.indent))
        }
    }

    private fun formatToken(stack: Stack, buffer: TokenBuffer) = iterator {
        when (buffer.current) {
            N3Token.Structural.BlankStart -> {
                TODO("Implementation required, similar to statements list, terminating statements with `;` tokens")
            }
            N3Token.Structural.StatementsListStart -> {
                yieldAll(formatStatementsBlock(stack, buffer))
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

    private fun formatStatementsBlock(stack: Stack, buffer: TokenBuffer) = iterator {
        require(buffer.current == N3Token.Structural.StatementsListStart)
        // adjusting current statement
        yield(buffer.current.syntax)
        buffer.advance()
        // starting the next statements (yielding relevant tokens) inside a new frame
        stack.startFrame()
        yield("\n")
        yield(indent.create(stack.indent))
        yieldAll(processStatements(stack, buffer))
        if (buffer.current != N3Token.Structural.StatementsListEnd) {
            throw IllegalStateException("Did reach the end of the statements list properly!")
        }
        // terminating this frame
        stack.stopFrame()
        yield("\n")
        yield(indent.create(stack.indent))
        yield(buffer.current.syntax)
        check(buffer.advance())
    }

    override fun format(tokens: Iterator<N3Token>): Iterator<String> {
        return processStatements(Stack(), TokenBuffer(tokens))
    }

}
