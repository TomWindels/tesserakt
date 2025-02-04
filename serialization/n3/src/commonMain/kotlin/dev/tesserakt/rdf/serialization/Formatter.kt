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
    val indent: String = "    ",
): Formatter() {

    override fun format(tokens: Iterator<N3Token>) = iterator {
        if (!tokens.hasNext()) {
            return@iterator
        }
        // stack position, with its # of elements representing the # of stack depth, and the actual number inside
        //  representing its own position inside of this stack
        val stack = Stack()
        var current = tokens.next()
        while (true) {
            // doing changes based on the token, before we emit it
            when {
                current.isStructuralSegmentEnd() -> {
                    stack.stopFrame()
                    yield("\n")
                    yield(indent.repeat(stack.indent))
                }
                else -> { /* nothing to do */ }
            }
            // actually emitting the token itself
            yield(current.syntax)
            // updating the structure using additional characters, but only if other tokens will follow
            if (!tokens.hasNext()) {
                return@iterator
            }
            val next = tokens.next()
            when {
                current.isStructuralSegmentStart() -> {
                    stack.startFrame()
                    yield("\n")
                    yield(indent.repeat(stack.indent))
                }
                (current.isPredicateEnd() || current.isStatementEnd()) && next.isStructuralSegmentEnd() -> {
                    /* nothing to do, next iteration will adjust position */
                }
                current.isStatementEnd() -> {
                    stack.set(Stack.Position.SUBJECT)
                    yield("\n")
                    yield(indent.repeat(stack.indent))
                }
                current.isPredicateEnd() -> {
                    stack.set(Stack.Position.PREDICATE)
                    yield("\n")
                    yield(indent.repeat(stack.indent))
                }
                current.isObjectEnd() -> {
                    stack.set(Stack.Position.OBJECT)
                    yield("\n")
                    yield(indent.repeat(stack.indent))
                }
                next is N3Token.TermToken -> {
                    stack.advance()
                    yield(" ")
                }
                else -> {
                    yield(" ")
                }
            }
            current = next
        }
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

    }

    private fun N3Token.isStructuralSegmentStart() =
        this == N3Token.Structural.BlankStart || this == N3Token.Structural.StatementsListStart

    private fun N3Token.isStructuralSegmentEnd() =
        this == N3Token.Structural.BlankEnd || this == N3Token.Structural.StatementsListEnd

    private fun N3Token.isPredicateEnd() =
        this == N3Token.Structural.PredicateTermination

    private fun N3Token.isObjectEnd() =
        this == N3Token.Structural.ObjectTermination

    private fun N3Token.isStatementEnd() =
        this == N3Token.Structural.StatementTermination

}
