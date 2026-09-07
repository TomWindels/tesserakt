package dev.tesserakt.rdf.types.impl

import dev.tesserakt.rdf.types.*
import dev.tesserakt.util.fit

/**
 * A foundation [Store] implementation, offering sane default implementations for [iter], [contains], [containsAll],
 *  and the various [Any] methods [toString], [equals] and [hashCode] to ensure correct Store behaviour.
 */
abstract class AbstractStore : Store {

    override fun iterator(): Iterator<Quad> = DecodingIterator(src = encodedIterator(), context = context)

    /**
     * Creates an [Iterator] that yields all [Quad]s present inside this [AbstractStore], for which the values [s],
     *  [p] and [o] match the parameters, when provided
     */
    override fun iter(s: Quad.Subject?, p: Quad.Predicate?, o: Quad.Object?, g: Quad.Graph?): Iterator<Quad> {
        return DecodingIterator(src = encodedIter(s, p, o, g), context = context)
    }

    /**
     * Creates an [Iterator] that yields all [EncodedQuad]s present inside this [Store], for which the values [s],
     *  [p], [o] and [g] match the parameters, or any if [Int.MIN_VALUE] is passed.
     */
    override fun encodedIter(
        s: EncodedQuadElement,
        p: EncodedQuadElement,
        o: EncodedQuadElement,
        g: EncodedQuadElement
    ): Iterator<EncodedQuad> {
        return if (s == Int.MIN_VALUE && p == Int.MIN_VALUE && o == Int.MIN_VALUE && g == Int.MIN_VALUE) {
            encodedIterator()
        } else {
            FilterIterator(encodedIterator(), s, p, o, g)
        }
    }

    override fun containsAll(elements: Collection<Quad>): Boolean {
        // if there's a distinct set of elements, and that number exceeds our own distinct set of elements in size,
        //  we cannot possibly contain them all
        if (elements is Set<*> && elements.size > this.size) {
            return false
        }
        return elements.all { it in this }
    }

    override fun asEncodedSet(): Set<EncodedQuad> {
        return object: Set<EncodedQuad> {

            override val size: Int
                get() = this@AbstractStore.size

            override fun contains(element: EncodedQuad): Boolean {
                return element in this@AbstractStore
            }

            override fun containsAll(elements: Collection<EncodedQuad>): Boolean {
                return elements.all { it in this@AbstractStore }
            }

            override fun isEmpty(): Boolean {
                return this@AbstractStore.isEmpty()
            }

            override fun iterator(): Iterator<EncodedQuad> {
                return this@AbstractStore.encodedIterator()
            }

        }
    }

    override fun toString() = if (isEmpty()) "<empty store>" else buildString {
        val previewed = this@AbstractStore.take(10)

        val s = previewed.map { it.s.toString() }
        val p = previewed.map { it.p.toString() }
        val o = previewed.map { it.o.toString() }
        val g = previewed.map { it.g.toString() }

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

        repeat(previewed.size) { i ->
            append(s[i].padEnd(sl))
            append(" | ")
            append(p[i].padEnd(pl))
            append(" | ")
            append(o[i].padEnd(ol))
            append(" | ")
            appendLine(g[i].padEnd(gl))
        }
        append("... ${this@AbstractStore.size} elements")
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is Set<*>) {
            return false
        }
        if (this.size != other.size) {
            return false
        }
        if (other is Store) {
            // we check hash code before content first, as read-only stores tend to cache this value, and thus can do
            //  this much faster
            if (this !is MutableStore && other !is MutableStore) {
                if (this.hashCode() != other.hashCode()) {
                    return false
                }
            }
            // we can use the context-mapper based 'contains all' check
            return containsAll(other)
        }
        // we're dealing with a regular set implementation, so we have to do a regular value-based lookup
        // we prefer decoding values over encoding them, so we check the contents of our own for presence in the
        //  other collection; considering it implements the `Set` interface, we expect the other type to have
        //  a direct lookup strategy (eg hash based) available
        return this.all { quad -> quad in other }
    }

    override fun hashCode(): Int {
        // going for accuracy instead of speed; non-mutable stores can cache this value
        // we use the hash code of the non-encoded representation so hash codes between different store implementations,
        //  using different encoding context implementations / instances, can still share a hash code (as they would
        //  be equal too)
        var result = 0
        forEach { quad -> result += quad.hashCode() }
        return result
    }

}

private class FilterIterator(
    private val src: Iterator<EncodedQuad>,
    private val s: EncodedQuadElement = Int.MIN_VALUE,
    private val p: EncodedQuadElement = Int.MIN_VALUE,
    private val o: EncodedQuadElement = Int.MIN_VALUE,
    private val g: EncodedQuadElement = Int.MIN_VALUE,
): Iterator<EncodedQuad> {

    private var next: EncodedQuad? = null

    override fun hasNext(): Boolean {
        if (next != null) {
            return true
        }
        next = getNext()
        return next != null
    }

    override fun next(): EncodedQuad {
        val result = next ?: getNext()
        next = null
        return result ?: throw NoSuchElementException()
    }

    private fun getNext(): EncodedQuad? {
        while (src.hasNext()) {
            val contender = src.next()
            if (satisfies(contender)) {
                return contender
            }
        }
        return null
    }

    private inline fun satisfies(quad: EncodedQuad): Boolean {
        return  (s == Int.MIN_VALUE || s == quad.s) &&
                (p == Int.MIN_VALUE || p == quad.p) &&
                (o == Int.MIN_VALUE || o == quad.o) &&
                (g == Int.MIN_VALUE || g == quad.g)
    }
}
