package dev.tesserakt.rdf.types.impl

import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.rdf.types.Store
import dev.tesserakt.util.fit
import dev.tesserakt.util.isNullOr
import dev.tesserakt.util.map

/**
 * A foundation [Store] implementation, offering sane default implementations for [iter], [contains], [containsAll],
 *  and the various [Any] methods [toString], [equals] and [hashCode] to ensure correct Store behaviour.
 */
abstract class AbstractStore : Store {

    /**
     * Creates an [Iterator] that yields all [Quad]s present inside this [AbstractStore], for which the values [s],
     *  [p] and [o] match the parameters, when provided
     */
    override fun iter(s: Quad.Subject?, p: Quad.Predicate?, o: Quad.Object?, g: Quad.Graph?): Iterator<Quad> {
        return if (s == null && p == null && o == null && g == null) {
            iterator()
        } else {
            FilterIterator(iterator(), s, p, o, g)
        }
    }

    override fun contains(element: Quad): Boolean {
        return iter(element.s, element.p, element.o).hasNext()
    }

    override fun containsAll(elements: Collection<Quad>): Boolean {
        return elements.all { it in this }
    }

    override fun toString() = if (isEmpty()) "<empty store>" else buildString {
        val s = this@AbstractStore.iterator().map { it.s.toString() }
        val p = this@AbstractStore.iterator().map { it.p.toString() }
        val o = this@AbstractStore.iterator().map { it.o.toString() }
        val g = this@AbstractStore.iterator().map { it.g.toString() }

        val sl = s.maxOf { it.length }
        val pl = p.maxOf { it.length }
        val ol = o.maxOf { it.length }
        val gl = g.maxOf { it.length }

        append("Subject".fit(sl))
        append(" | ")
        append("Predicate".fit(pl))
        append(" | ")
        append("Object".fit(ol))
        append(" | ")
        appendLine("Graph".fit(gl))

        repeat(this@AbstractStore.size) { i ->
            append(s[i].padEnd(sl))
            append(" | ")
            append(p[i].padEnd(pl))
            append(" | ")
            append(o[i].padEnd(ol))
            append(" | ")
            appendLine(g[i].padEnd(gl))
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is AbstractStore) {
            return false
        }
        return this.size == other.size && containsAll(other)
    }

    override fun hashCode(): Int {
        // going for accuracy instead of speed; non-mutable stores can cache this value
        var result = 0
        forEach { quad -> result += quad.hashCode() }
        return result
    }

}

private class FilterIterator(
    private val src: Iterator<Quad>,
    private val s: Quad.Subject?,
    private val p: Quad.Predicate?,
    private val o: Quad.Object?,
    private val g: Quad.Graph?,
): Iterator<Quad> {

    private var next: Quad? = null

    override fun hasNext(): Boolean {
        if (next != null) {
            return true
        }
        next = getNext()
        return next != null
    }

    override fun next(): Quad {
        val result = next ?: getNext()
        next = null
        return result ?: throw NoSuchElementException()
    }

    private fun getNext(): Quad? {
        while (src.hasNext()) {
            val contender = src.next()
            if (satisfies(contender)) {
                return contender
            }
        }
        return null
    }

    private inline fun satisfies(quad: Quad): Boolean {
        return  s.isNullOr { it == quad.s } &&
                p.isNullOr { it == quad.p } &&
                o.isNullOr { it == quad.o } &&
                g.isNullOr { it == quad.g }
    }
}
