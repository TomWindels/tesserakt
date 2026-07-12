package dev.tesserakt.rdf.types

/**
 * The base encoding context type. Used to convert [Quad] to [EncodedQuad]s and vice versa.
 *
 * Note that the base type is **immutable**, meaning that [Quad]s containing [Quad.Element] not known to
 *  this [EncodingContext] instance **cannot** be encoded. For mutable contexts that *can* encode *any* quad,
 *  see [MutableEncodingContext]. Note that most [Store]s do not expose such a context as these are managed by
 *  the [Store] directly.
 */
interface EncodingContext {

    fun encode(element: Quad.Element): Int?

    fun decode(encoded: Int): Quad.Element?

}
