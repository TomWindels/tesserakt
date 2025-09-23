package dev.tesserakt.rdf.trig.serialization

internal class TokenBuffer(private val source: Iterator<TriGToken>) {

    private var current: TriGToken = if (source.hasNext()) source.next() else TriGToken.EOF

    fun peek(): TriGToken {
        return current
    }

    fun peekOrNull(): TriGToken? {
        return current.takeIf { it != TriGToken.EOF }
    }

    /**
     * Consumes the token, yielding it one last time. The result is identical to calling [peek] followed
     *  by [consume] (when discarding the result):
     * ```kt
     * val token = buffer.peek()
     * check(token == buffer.consume())
     * ```
     */
    fun consume(): TriGToken {
        val original = current
        current = if (source.hasNext()) source.next() else TriGToken.EOF
        return original
    }

    fun hasNext(): Boolean {
        return current != TriGToken.EOF
    }

}
