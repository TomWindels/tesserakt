package dev.tesserakt.rdf.types

/**
 * The encoded representation of a [Quad.Element] managed by an [EncodingContext]. The valid integer range of an encoded
 *  quad element is limited to `0..`[Int.MAX_VALUE]. The negative range is reserved for use 'external to [Store]s', e.g.
 *  encoding terms found in SPARQL queries.
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
     * The number of elements currently encoded. It is guaranteed that all [encode]d terms yielded by this context
     *  are in the `0..<`[size] range.
     *
     * NOTE: if the implementation is mutable (implements the [MutableEncodingContext] interface), this value can
     *  change! See [MutableEncodingContext.size] for more details.
     */
    val size: Int

    /**
     * Encodes the [element] into an [EncodedQuadElement] in the `0..`[size] range. Returns `null` if this context is
     *  immutable and the requested [element] was not made encodable by this context.
     */
    fun encode(element: Quad.Element): EncodedQuadElement?

    fun decode(encoded: EncodedQuadElement): Quad.Element?

}
