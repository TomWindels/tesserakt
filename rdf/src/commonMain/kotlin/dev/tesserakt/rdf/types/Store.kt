package dev.tesserakt.rdf.types

interface Store : Set<Quad> {

    /**
     * The context used to encode and decode [Quad.Element]s
     */
    val context: EncodingContext

    /**
     * Similar to [iterator], yielding the [EncodedQuad] representation of the various elements present in this store
     */
    fun encodedIterator(): Iterator<EncodedQuad>

    /**
     * Creates an [Iterator] that yields all [Quad]s present inside this [Store], for which the values [s],
     *  [p] and [o] match the parameters, when provided
     */
    fun iter(s: Quad.Subject? = null, p: Quad.Predicate? = null, o: Quad.Object? = null, g: Quad.Graph? = null): Iterator<Quad>

    /**
     * Creates an [Iterator] that yields all [EncodedQuad]s present inside this [Store], for which the values [s],
     *  [p] and [o] match the parameters, when provided
     */
    fun encodedIter(s: Quad.Subject? = null, p: Quad.Predicate? = null, o: Quad.Object? = null, g: Quad.Graph? = null): Iterator<EncodedQuad>

}
