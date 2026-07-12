package dev.tesserakt.rdf.types.impl

import dev.tesserakt.rdf.types.EncodedQuad
import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.rdf.types.Store

internal class StoreImpl(data: Collection<Quad>): AbstractStore() {

    // we first create our encoding context
    override val context = ImmutableEncodingContextImpl(data)

    // then we create our actual set of *encoded* quads
    // the fact they're encoded makes checking for 'contains' etc. faster, as we mainly pay the price for initial lookup
    //  of the individual terms, and can then compare the encoded variants faster
    private val quads = run {
        val result = mutableSetOf<EncodedQuad>()
        data.forEach { quad ->
            // we can guarantee that our encoding context has all necessary terms as it was made with the same quad
            //  collection as an argument
            val encoded = EncodedQuad(context, quad)
                ?: throw IllegalStateException("Encoding context did not uphold contract!")
            result.add(encoded)
        }
        // we hide the fact that we're mutable
        result as Set<EncodedQuad>
    }

    // considering the contents don't change, we can cache the collection's hash code
    private val hashCode by lazy {
        var result = 0
        quads.forEach { quad -> result += quad.hashCode() }
        result
    }

    override val size: Int
        get() = quads.size

    override fun encodedIterator(): Iterator<EncodedQuad> = quads.iterator()

    override fun isEmpty(): Boolean {
        return quads.isEmpty()
    }

    override fun containsAll(elements: Collection<Quad>): Boolean {
        return elements.all { this.contains(it) }
    }

    override fun contains(element: Quad): Boolean {
        // two possible scenarios where we don't contain a given quad:
        // * either our immutable context doesn't have it, in which case we don't contain a quad element this quad uses,
        //  and thus cannot possibly contain the quad, or
        // * we have all individual quad elements present in our context, but not in this
        //  specific 'configuration' (s, p, o and g)
        val encoded = EncodedQuad(context, element)
            // case 1: the quad has an element we don't even have an encoded representation for; so we don't have the
            //  quad itself either
            ?: return false
        // case 2: we have to check the encoded representation in our collection
        return quads.contains(encoded)
    }

    override fun hashCode(): Int {
        return hashCode
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is Store) {
            return false
        }
        return this.size == other.size && containsAll(other)
    }

}
