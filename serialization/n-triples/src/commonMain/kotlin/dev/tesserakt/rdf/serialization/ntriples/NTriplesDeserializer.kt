package dev.tesserakt.rdf.serialization.ntriples

import dev.tesserakt.rdf.serialization.InternalSerializationApi
import dev.tesserakt.rdf.serialization.util.BufferedCharStream
import dev.tesserakt.rdf.serialization.util.expect
import dev.tesserakt.rdf.serialization.util.nextIs
import dev.tesserakt.rdf.serialization.util.nextIsEofOr
import dev.tesserakt.rdf.serialization.util.peekOrBail
import dev.tesserakt.rdf.types.Quad
import kotlin.text.isWhitespace

@InternalSerializationApi
internal class NTriplesDeserializer(private val source: BufferedCharStream) : Iterator<Quad> {

    private val lut = mutableMapOf<String, Int>()
    private var next: Quad? = null

    override fun hasNext(): Boolean {
        if (next != null) {
            return true
        }
        next = getNext()
        return next != null
    }

    override fun next(): Quad {
        val current = next ?: getNext()
        next = null
        return current ?: throw NoSuchElementException()
    }

    private fun getNext(): Quad? {
        val s = consumeTerm() ?: return null
        check(s is Quad.Subject)
        val p = (consumeTerm() ?: throw IllegalStateException("Predicate is missing!")).let { term ->
            term as? Quad.NamedTerm ?: throw IllegalStateException("Expected a named term, but got $term instead!")
        }
        val o = consumeTerm() ?: throw IllegalStateException("Object is missing!")
        check(o is Quad.Object)
        consumeWhitespace()
        check(source.nextIs('.')) {
            "Failed reaching the end of the statement. Read terms $s $p $o"
        }
        source.consume()
        return Quad(s, p, o)
    }

    private fun consumeTerm(): Quad.Element? {
        consumeWhitespace()
        val code = source.pop()
        if (code == -1) {
            return null
        }
        return when (val c = Char(code)) {
            '<' -> {
                val inner = source.consumeAndDecodeWithoutWhitespaceUntil('>')
                source.consume() // '>'
                Quad.NamedTerm(inner)
            }

            '_' -> {
                source.expect(':')
                source.consume()
                val label = source.consumeUntilWhitespace()
                Quad.BlankTerm(id = label.asBlankNodeId())
            }

            '"' -> {
                val value = consumeUntilUnescaped('"')
                source.consume()
                if (source.nextIs('^')) {
                    source.consume()
                    source.expect('^')
                    source.consume()
                    val dt = consumeTerm()
                    check(dt is Quad.NamedTerm) { "$dt is not a valid data type for a literal!" }
                    Quad.Literal(value = value, type = dt)
                } else if (source.nextIs('@')) {
                    // getting rid of the '@'
                    source.consume()
                    val lang = source.consumeUntilWhitespace()
                    Quad.Literal(value = value, language = lang)
                } else {
                    Quad.Literal(value = value)
                }
            }

            else -> {
                throw IllegalStateException("Unexpected character `$c`")
            }
        }
    }

    private fun consumeWhitespace() {
        while (true) {
            if (source.nextIs('#')) {
                while (source.peek().let { it != -1 && Char(it) != '\n' }) {
                    source.consume()
                }
                source.consume()
            }
            if (source.nextIsEofOr { !it.isWhitespace() }) {
                break
            }
            source.consume()
        }
    }

    private inline fun consumeUntilUnescaped(char: Char): String {
        val result = StringBuilder()
        var c = source.peekOrBail()
        var escaped = false
        while (escaped || c != char) {
            result.append(c)
            source.consume()
            escaped = !escaped && c == '\\'
            c = source.peekOrBail()
        }
        return result.toString()
    }

    private fun String.asBlankNodeId() = lut.getOrPut(this) { lut.size }

}
