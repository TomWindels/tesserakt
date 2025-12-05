package dev.tesserakt.rdf.serialization.ntriples

import dev.tesserakt.rdf.ontology.XSD
import dev.tesserakt.rdf.serialization.InternalSerializationApi
import dev.tesserakt.rdf.serialization.util.BufferedString
import dev.tesserakt.rdf.serialization.util.bail
import dev.tesserakt.rdf.serialization.util.consumeWhile
import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.util.isNullOr

@InternalSerializationApi
internal class NTriplesDeserializer(private val source: BufferedString) : Iterator<Quad> {

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
        check(source.peek() == '.') {
            "Failed reaching the end of the statement. Read terms $s $p $o"
        }
        source.consume()
        return Quad(s, p, o)
    }

    private fun consumeTerm(): Quad.Element? {
        consumeWhitespace()
        return when (val c = source.pop()) {
            null -> return null
            '<' -> {
                val inner = source.consumeWhile(Char::isWhitespace) { it != '>' }
                source.consume() // '>'
                Quad.NamedTerm(inner)
            }

            '_' -> {
                check(source.peek() == ':')
                source.consume()
                val label = source.consumeWhile { !it.isWhitespace() }
                Quad.BlankTerm(id = label.asBlankNodeId())
            }

            '"' -> {
                val value = consumeUntilUnescaped('"')
                source.consume()
                if (source.peek() == '^') {
                    source.consume()
                    check(source.peek() == '^')
                    source.consume()
                    val dt = consumeTerm()
                    check(dt is Quad.NamedTerm) { "$dt is not a valid data type for a literal!" }
                    Quad.Literal(value = value, type = dt)
                } else if (source.peek() == '@') {
                    // getting rid of the '@'
                    source.consume()
                    val lang = source.consumeWhile { !it.isWhitespace() }
                    Quad.LangString(value = value, language = lang)
                } else {
                    Quad.Literal(value = value, type = XSD.string)
                }
            }

            else -> {
                throw IllegalStateException("Unexpected character `$c`")
            }
        }
    }

    private fun consumeWhitespace() {
        while (true) {
            if (source.peek() == '#') {
                while (source.peek().let { it != null && it != '\n' }) {
                    source.consume()
                }
                source.consume()
            }
            if (source.peek().isNullOr { !it.isWhitespace() }) {
                break
            }
            source.consume()
        }
    }

    private inline fun consumeUntilUnescaped(char: Char): String {
        val result = StringBuilder()
        var c = source.peek(0) ?: source.bail("Unexpected EOF reached!")
        var escaped = false
        while (escaped || c != char) {
            result.append(source.peek(0))
            source.consume()
            escaped = !escaped && c == '\\'
            c = source.peek(0) ?: source.bail("Unexpected EOF reached!", -result.length .. 0)
        }
        return result.toString()
    }

    private fun String.asBlankNodeId() = lut.getOrPut(this) { lut.size }

}
