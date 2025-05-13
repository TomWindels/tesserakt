package dev.tesserakt.rdf.turtle.serialization

internal class TokenBuffer(private val source: Iterator<TurtleToken>) {

    private var current: TurtleToken = if (source.hasNext()) source.next() else TurtleToken.EOF

    fun peek(): TurtleToken {
        return current
    }

    fun peekOrNull(): TurtleToken? {
        return current.takeIf { it != TurtleToken.EOF }
    }

    fun consume(): TurtleToken {
        current = if (source.hasNext()) source.next() else TurtleToken.EOF
        return current
    }

    fun hasNext(): Boolean {
        return current != TurtleToken.EOF
    }

}
