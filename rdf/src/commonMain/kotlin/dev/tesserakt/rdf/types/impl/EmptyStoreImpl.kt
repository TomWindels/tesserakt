package dev.tesserakt.rdf.types.impl

import dev.tesserakt.rdf.types.*

internal object EmptyStoreImpl : Store {

    override val context: EncodingContext = EmptyEncodingContext

    override val size: Int
        get() = 0

    override fun contains(element: Quad) = false

    override fun containsAll(elements: Collection<Quad>) = false

    override fun isEmpty() = true

    override fun iterator(): Iterator<Quad> = emptyIterator()

    override fun iter(s: Quad.Subject?, p: Quad.Predicate?, o: Quad.Object?, g: Quad.Graph?): Iterator<Quad> =
        emptyIterator()

    override fun encodedIterator(): Iterator<EncodedQuad> = emptyIterator()

    override fun encodedIter(
        s: EncodedQuadElement,
        p: EncodedQuadElement,
        o: EncodedQuadElement,
        g: EncodedQuadElement
    ): Iterator<EncodedQuad> = emptyIterator()

    override fun encodedIter(
        s: Quad.Subject?,
        p: Quad.Predicate?,
        o: Quad.Object?,
        g: Quad.Graph?
    ): Iterator<EncodedQuad> = emptyIterator()

    override fun toString(): String = "<empty store>"

    override fun hashCode() = 0

}
