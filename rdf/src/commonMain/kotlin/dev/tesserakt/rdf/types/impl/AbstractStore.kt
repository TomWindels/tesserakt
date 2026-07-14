package dev.tesserakt.rdf.types.impl

import dev.tesserakt.rdf.types.EncodedQuad
import dev.tesserakt.rdf.types.EncodedQuadElement
import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.rdf.types.Store
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
        if (elements is Store) {
            // as this is a store, it uses set semantics;
            //  if, after set semantics, it contains more quads than we do, we cannot possibly contain all of them
            if (size < elements.size) {
                return false
            }
            // we construct a mapping context between us and the other store, but only if the elements we need to look
            //  up count-wise 'is worth it' (as the mapper looks up the entire context immediately)
            if (this.context.size > elements.size * 3) {
                // our context is too large w.r.t. the amount of lookups we need to do;
                //  we do the lookups as is needed instead
                return elements.all { it in this }
            }
            val mapper = ContextMapper(source = elements.context, target = this.context)
            elements.encodedIterator().forEach { encodedQuad ->
                val reencoded = mapper.reencode(encodedQuad) ?: return false
                if (reencoded !in this) {
                    return false
                }
            }
            // all elements matched after reencoding, so we contain all elements from the other store
            return true
        }

        return elements.all { it in this }
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
