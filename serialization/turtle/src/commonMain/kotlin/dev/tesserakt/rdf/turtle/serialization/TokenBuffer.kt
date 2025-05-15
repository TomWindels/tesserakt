package dev.tesserakt.rdf.turtle.serialization

internal class TokenBuffer(private val source: Iterator<TurtleToken>) {

    private var current: TurtleToken = if (source.hasNext()) source.next() else TurtleToken.EOF

    fun peek(): TurtleToken {
        return current
    }

    fun peekOrNull(): TurtleToken? {
        return current.takeIf { it != TurtleToken.EOF }
    }

    /**
     * Consumes the token, yielding it one last time. The result is identical to calling [peek] followed
     *  by [consume] (when discarding the result):
     * ```kt
     * val token = buffer.peek()
     * check(token == buffer.consume())
     * ```
     */
    fun consume(): TurtleToken {
        val original = current
        current = if (source.hasNext()) source.next() else TurtleToken.EOF
        return original
    }

    fun hasNext(): Boolean {
        return current != TurtleToken.EOF
    }

}
