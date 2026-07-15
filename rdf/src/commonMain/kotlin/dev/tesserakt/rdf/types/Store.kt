package dev.tesserakt.rdf.types

import dev.tesserakt.rdf.types.impl.emptyIterator

interface Store : Set<Quad> {

    /**
     * The context used to encode and decode [Quad.Element]s
     */
    val context: EncodingContext

    /**
     * Similar to [iterator], yielding the [EncodedQuad] representation of the various elements present in this store
     */
    fun encodedIterator(): Iterator<EncodedQuad>

    override fun contains(element: Quad): Boolean {
        return iter(element.s, element.p, element.o, element.g).hasNext()
    }

    operator fun contains(quad: EncodedQuad): Boolean {
        return encodedIter(quad.s, quad.p, quad.o, quad.g).hasNext()
    }

    /**
     * Creates an [Iterator] that yields all [Quad]s present inside this [Store], for which the values [s],
     *  [p] and [o] match the parameters, when provided
     */
    fun iter(
        s: Quad.Subject? = null,
        p: Quad.Predicate? = null,
        o: Quad.Object? = null,
        g: Quad.Graph? = null
    ): Iterator<Quad>

    /**
     * Creates an [Iterator] that yields all [EncodedQuad]s present inside this [Store], for which the values [s],
     *  [p], [o] and [g] match the parameters, or any if [Int.MIN_VALUE] is passed.
     */
    fun encodedIter(
        s: EncodedQuadElement = Int.MIN_VALUE,
        p: EncodedQuadElement = Int.MIN_VALUE,
        o: EncodedQuadElement = Int.MIN_VALUE,
        g: EncodedQuadElement = Int.MIN_VALUE
    ): Iterator<EncodedQuad>

    /**
     * Creates an [Iterator] that yields all [EncodedQuad]s present inside this [Store], for which the values [s],
     *  [p], [o] and [g] match the parameters, when provided
     */
    fun encodedIter(
        s: Quad.Subject? = null,
        p: Quad.Predicate? = null,
        o: Quad.Object? = null,
        g: Quad.Graph? = null
    ): Iterator<EncodedQuad> {
        val ctx = (context as? MutableEncodingContext)?.asReadOnlyEncodingContext() ?: context
        // if the immutable context above fails to encode a supplied parameter, we can bail early, as there's no quad
        //  containing the term in the collection
        return encodedIter(
            s = s?.let { ctx.encode(it) ?: return emptyIterator() } ?: Int.MIN_VALUE,
            p = p?.let { ctx.encode(it) ?: return emptyIterator() } ?: Int.MIN_VALUE,
            o = o?.let { ctx.encode(it) ?: return emptyIterator() } ?: Int.MIN_VALUE,
            g = g?.let { ctx.encode(it) ?: return emptyIterator() } ?: Int.MIN_VALUE,
        )
    }

}
