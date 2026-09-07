package dev.tesserakt.rdf.types

/**
 * The encoded representation of a [Quad.Element] managed by an [EncodingContext]. The valid integer range of an encoded
 *  quad element is limited to ][Int.MIN_VALUE], [Int.MAX_VALUE]]. [Int.MIN_VALUE] itself is reserved as sentinel value
 *  for encoding, or 'wildcard' during lookup.
 */
typealias EncodedQuadElement = Int

/**
 * The base encoding context type. Used to convert [Quad] to [EncodedQuad]s and vice versa.
 *
 * Note that the base type is **immutable**, meaning that [Quad]s containing [Quad.Element]s not known to
 *  this [EncodingContext] instance **cannot** be encoded. For mutable contexts that *can* encode *any* quad,
 *  see [MutableEncodingContext].
 *
 * Note that most [Store]s do not expose their context as mutable as these are managed by the [Store] directly.
 */
interface EncodingContext {

    /**
     * Encodes the [element] into an [EncodedQuadElement]. Returns `null` if this context is
     *  immutable and the requested [element] was not made encodable by this context.
     */
    fun encode(element: Quad.Element): EncodedQuadElement?

    fun decode(encoded: EncodedQuadElement): Quad.Element?

}
