package dev.tesserakt.rdf.types

/**
 * A mutable version of the [EncodingContext]. As this variant allows for new [Quad.Element]s to be encoded, it can be
 *  used to encode any [Quad].
 */
interface MutableEncodingContext: EncodingContext {

    /**
     * The number of elements currently encoded. It is guaranteed that all [encode]d terms yielded by this context
     *  are in the `0..<`[size] range.
     *
     * Some implementations support the deletion of encoded terms. This deletion of encoded terms **is not guaranteed
     *  to decrease this value**, as doing so would break the contract requirement established by [EncodingContext.size].
     */
    override val size: Int

    /**
     * Encodes the [element] into an [EncodedQuadElement] in the `0..`[size] range. If the requested [element] is not
     *  currently encodable by this context, it will add it to the context, possibly increasing the [size].
     */
    override fun encode(element: Quad.Element): EncodedQuadElement

    /**
     * Creates a (shallow) copy of this instance adhering to the [EncodingContext] interface, resulting in immutable
     *  [EncodingContext.encode] semantics.
     */
    fun asReadOnlyEncodingContext(): EncodingContext

}
