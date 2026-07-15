package dev.tesserakt.rdf.types.impl

import dev.tesserakt.rdf.types.EncodingContext
import dev.tesserakt.rdf.types.MutableEncodingContext
import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.util.getOrInsert

internal class MutableEncodingContextImpl private constructor(
    private val encoder: MutableMap<Quad.Element, Int>,
    // as we currently do not support the deletion of encoded quad terms, encoded values always increment
    private val decoder: MutableList<Quad.Element>,
): MutableEncodingContext {

    constructor(): this(
        encoder = mutableMapOf(),
        decoder = mutableListOf(),
    )

    override val size: Int
        get() = decoder.size

    override fun encode(element: Quad.Element): Int {
        return encoder.getOrInsert(element) {
            val i = decoder.size
            decoder.add(element)
            i
        }
    }

    override fun decode(encoded: Int): Quad.Element? {
        if (encoded !in decoder.indices) {
            return null
        }
        return decoder[encoded]
    }

    override fun clear() {
        encoder.clear()
        decoder.clear()
    }

    // internal is technically not required as we're already inside an internal class
    internal fun encoder(): Map<Quad.Element, Int> {
        return encoder
    }

    // internal is technically not required as we're already inside an internal class
    internal fun decoder(): List<Quad.Element> {
        return decoder
    }

    /**
     * Creates an immutable variant of this context, so converting between [Quad]s
     * and [dev.tesserakt.rdf.types.EncodedQuad]s through this read-only copy does not alter this encoder's state.
     */
    override fun asReadOnlyEncodingContext(): EncodingContext {
        return ImmutableEncodingContextImpl(reference = this)
    }

}
