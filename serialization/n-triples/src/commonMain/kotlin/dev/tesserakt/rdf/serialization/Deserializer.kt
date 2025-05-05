package dev.tesserakt.rdf.serialization

import dev.tesserakt.rdf.ontology.RDF
import dev.tesserakt.rdf.ontology.XSD
import dev.tesserakt.rdf.serialization.util.BufferedString
import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.util.isNullOr

internal class Deserializer(private val source: BufferedString) : Iterator<Quad> {

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
        val p = (consumeTerm() ?: throw IllegalStateException("Predicate is missing!")).let { term ->
            term as? Quad.NamedTerm ?: throw IllegalStateException("Expected a named term, but got $term instead!")
        }
        val o = consumeTerm() ?: throw IllegalStateException("Object is missing!")
        consumeWhitespace()
        check(source.peek() == '.') {
            "Failed reaching the end of the statement. Read terms $s $p $o"
        }
        source.consume()
        return Quad(s, p, o)
    }

    private fun consumeTerm(): Quad.Term? {
        consumeWhitespace()
        return when (val c = source.peek().also { source.consume() }) {
            null -> return null
            '<' -> {
                val inner = consumeWhile { it != '>' }
                source.consume() // '>'
                Quad.NamedTerm(inner)
            }

            '_' -> {
                check(source.peek() == ':')
                source.consume()
                val label = consumeWhile { !it.isWhitespace() }
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
                    // FIXME use the lang tag
                    consumeWhile { !it.isWhitespace() }
                    Quad.Literal(value = value, type = RDF.langString)
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

    private inline fun consumeWhile(predicate: (Char) -> Boolean): String {
        val result = StringBuilder()
        while (predicate(source.peek(0) ?: throw NoSuchElementException("Unexpected EOF reached!"))) {
            result.append(source.peek(0))
            source.consume()
        }
        return result.toString()
    }

    private inline fun consumeUntilUnescaped(char: Char): String {
        val result = StringBuilder()
        var c = source.peek(0) ?: throw NoSuchElementException("Unexpected EOF reached!")
        var escaped = false
        while (escaped || c != char) {
            result.append(source.peek(0))
            source.consume()
            escaped = !escaped && c == '\\'
            c = source.peek(0) ?: throw NoSuchElementException("Unexpected EOF reached!")
        }
        return result.toString()
    }

    private fun String.asBlankNodeId() = lut.getOrPut(this) { lut.size }

}
