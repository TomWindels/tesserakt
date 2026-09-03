package dev.tesserakt.rdf.types

import dev.tesserakt.rdf.types.impl.ConcurrentMutableEncodingContextImpl

/**
 * A mutable version of the [EncodingContext]. As this variant allows for new [Quad.Element]s to be encoded, it can be
 *  used to encode any [Quad].
 */
interface MutableEncodingContext: EncodingContext {

    /**
     * Encodes the [element] into an [EncodedQuadElement]. If the requested [element] is not
     *  currently encodable by this context, it will add it to the context.
     */
    override fun encode(element: Quad.Element): EncodedQuadElement

    /**
     * Clears all encoded terms, invalidating any [EncodedQuadElement] values that may have been created by this context
     */
    fun clear()

    /**
     * Creates a (shallow) copy of this instance adhering to the [EncodingContext] interface, resulting in immutable
     *  [EncodingContext.encode] semantics.
     */
    fun asReadOnlyEncodingContext(): EncodingContext

    companion object

}

fun MutableEncodingContext.Companion.concurrent(): MutableEncodingContext {
    return ConcurrentMutableEncodingContextImpl()
}
