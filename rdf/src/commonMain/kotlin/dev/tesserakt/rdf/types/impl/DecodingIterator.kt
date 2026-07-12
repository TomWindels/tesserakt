package dev.tesserakt.rdf.types.impl

import dev.tesserakt.rdf.types.EncodedQuad
import dev.tesserakt.rdf.types.EncodingContext
import dev.tesserakt.rdf.types.Quad

/**
 * An [Iterator] that uses the provided [dev.tesserakt.rdf.types.EncodingContext] to decode
 *  incoming [dev.tesserakt.rdf.types.EncodedQuad]s. Throws [IllegalStateException] if the context cannot decode the
 *  next quad.
 */
internal class DecodingIterator(
    private val src: Iterator<EncodedQuad>,
    private val context: EncodingContext,
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
        val n = next ?: getNext() ?: throw NoSuchElementException()
        next = null
        return n
    }

    private fun getNext(): Quad? {
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
        return decoded
    }

}
