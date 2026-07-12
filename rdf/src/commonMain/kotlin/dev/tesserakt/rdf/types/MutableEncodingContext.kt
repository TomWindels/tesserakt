package dev.tesserakt.rdf.types

/**
 * A mutable version of the [EncodingContext]. As this variant allows for new [Quad.Element]s to be encoded, it can be
 *  used to encode any [Quad].
 */
interface MutableEncodingContext: EncodingContext {

    override fun encode(element: Quad.Element): Int

    fun asReadOnlyEncodingContext(): EncodingContext

}
