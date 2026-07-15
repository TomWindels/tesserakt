package dev.tesserakt.rdf.types.impl

import dev.tesserakt.rdf.types.EncodedQuad
import dev.tesserakt.rdf.types.EncodingContext
import dev.tesserakt.rdf.types.Quad

/**
 * A version of the [MutableDecodingIterator] that also yields its incoming [EncodedQuad]s for pairwise processing.
 */
internal class MutableDecodingPairIterator(
    private val src: MutableIterator<EncodedQuad>,
    private val context: EncodingContext,
): MutableIterator<Pair<EncodedQuad, Quad>> {

    private var next: Pair<EncodedQuad, Quad>? = null

    override fun hasNext(): Boolean {
        if (next != null) {
            return true
        }
        next = getNext()
        return next != null
    }

    override fun next(): Pair<EncodedQuad, Quad> {
        val n = next ?: getNext() ?: throw NoSuchElementException()
        next = null
        return n
    }

    private fun getNext(): Pair<EncodedQuad, Quad>? {
        if (!src.hasNext()) {
            return null
        }
        val encoded = src.next()
        val decoded = Quad(
            s = context.decode(encoded.s) as? Quad.Subject
                ?: throw IllegalStateException("Failed to decode quad!"),
            p = context.decode(encoded.p) as? Quad.Predicate
                ?: throw IllegalStateException("Failed to decode quad!"),
            o = context.decode(encoded.o) as? Quad.Object
                ?: throw IllegalStateException("Failed to decode quad!"),
            g = context.decode(encoded.g) as? Quad.Graph
                ?: throw IllegalStateException("Failed to decode quad!"),
        )
        return encoded to decoded
    }

    override fun remove() {
        src.remove()
    }

}
