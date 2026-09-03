package dev.tesserakt.rdf.types.impl

import dev.tesserakt.rdf.types.EncodingContext
import dev.tesserakt.rdf.types.Quad

internal object EmptyEncodingContext: EncodingContext {

    override fun encode(element: Quad.Element): Int? {
        return null
    }

    override fun decode(encoded: Int): Quad.Element? {
        return null
    }

}
