package dev.tesserakt.rdf.types.impl

import dev.tesserakt.rdf.types.EncodingContext
import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.util.getOrInsert
import kotlin.jvm.JvmName

internal class ImmutableEncodingContextImpl private constructor(
    private val encoder: Map<Quad.Element, Int>,
    // no known encoder implementation allows for 'holes' in the encoded indices (deletion of encoded quad elements is
    //  not supported currently), so we can use a regular array
    private val decoder: List<Quad.Element>,
): EncodingContext {

    override fun encode(element: Quad.Element): Int? {
        return encoder[element]
    }

    override fun decode(encoded: Int): Quad.Element? {
        if (encoded !in decoder.indices) {
            return null
        }
        return decoder[encoded]
    }

    companion object {

        /**
         * Constructs a new [ImmutableEncodingContextImpl] that can encode and decode all of the requested [elements].
         */
        @JvmName("fromQuadElements")
        operator fun invoke(elements: Iterable<Quad.Element>): ImmutableEncodingContextImpl {
            val encoded = mutableMapOf<Quad.Element, Int>()
            var i = 0
            elements.forEach { element ->
                encoded.getOrInsert(element) { i++ }
            }
            val decoded = MutableList<Quad.Element?>(encoded.size) { null }
            encoded.forEach { (element, encoded) -> decoded[encoded] = element }
            // they have now all been set so we know none of the elements are `null`
            @Suppress("UNCHECKED_CAST")
            decoded as List<Quad.Element>
            return ImmutableEncodingContextImpl(
                encoder = encoded,
                decoder = decoded,
            )
        }

        /**
         * Constructs a new [ImmutableEncodingContextImpl] that can encode and decode all of the requested [quads].
         */
        @JvmName("fromQuads")
        operator fun invoke(quads: Iterable<Quad>): ImmutableEncodingContextImpl {
            return invoke(
                elements = sequence {
                    quads.forEach { quad ->
                        yield(quad.s)
                        yield(quad.p)
                        yield(quad.o)
                        yield(quad.g)
                    }
                }.asIterable()
            )
        }

        /**
         * Creates a new [EncodingContext] instance with all [Quad.Element]s present in the [reference] context.
         * It shares the underlying encoder/decoder pairs found in the [reference] context, as removal of encoded
         *  elements is not supported.
         */
        operator fun invoke(reference: MutableEncodingContextImpl): ImmutableEncodingContextImpl {
            return ImmutableEncodingContextImpl(
                encoder = reference.encoder(),
                decoder = reference.decoder(),
            )
        }

    }

}
