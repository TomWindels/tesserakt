package dev.tesserakt.rdf.types.impl

import dev.tesserakt.rdf.types.EncodedQuad
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

    override val size: Int
        get() = decoder.size

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
         * Constructs a new [ImmutableEncodingContextImpl] that can encode and decode all of the requested [quads].
         * Outputs the encoded representation to [output], so that an additional lookup can be prevented
         */
        @JvmName("fromQuads")
        operator fun invoke(quads: Iterable<Quad>, output: MutableSet<EncodedQuad>): ImmutableEncodingContextImpl {
            val encoded: MutableMap<Quad.Element, Int>
            val decoded: MutableList<Quad.Element>
            if (quads is Collection<*>) {
                // we approximate an initial capacity, expecting various subject / objects
                //  to be unique, whilst most predicate and graph terms to be reused frequently
                val cap = quads.size * 2
                encoded = HashMap(cap)
                decoded = ArrayList(cap)
            } else {
                encoded = mutableMapOf()
                decoded = mutableListOf()
            }
            fun encode(element: Quad.Element): Int {
                return encoded.getOrInsert(element) {
                    val pos = decoded.size
                    decoded.add(element)
                    pos
                }
            }
            quads.forEach { quad ->
                val encoded = EncodedQuad(
                    s = encode(quad.s),
                    p = encode(quad.p),
                    o = encode(quad.o),
                    g = encode(quad.g),
                )
                output.add(encoded)
            }
            // they have now all been set so we know none of the elements are `null`
            return ImmutableEncodingContextImpl(
                encoder = encoded,
                decoder = decoded,
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
