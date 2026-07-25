package dev.tesserakt.rdf.types.impl

import dev.tesserakt.rdf.types.EncodedQuad
import dev.tesserakt.rdf.types.EncodingContext

/**
 * A mapper between two [EncodingContext]s, making direct mapping possible for comparison purposes.
 *
 * NOTE: this only compares the immutable versions: the LUT is constructed eagerly. Changes to either contexts are
 *  therefore not reflected in the mapper, and will lead to incorrect results.
 *
 * NOTE: as this creates a full transformation table between the two contexts, it is generally discouraged to create a
 *  mapper when the number of conversions is limited w.r.t. the respective encoding context sizes.
 */
internal class ContextMapper(
    /**
     * The context that **was used** to construct 'source'
     *  encoded [dev.tesserakt.rdf.types.Quad.Element]s / [dev.tesserakt.rdf.types.EncodedQuad]s
     */
    source: EncodingContext,
    /**
     * The context that **was used** to construct 'target'
     *  encoded [dev.tesserakt.rdf.types.Quad.Element]s / [dev.tesserakt.rdf.types.EncodedQuad]s
     */
    target: EncodingContext,
) {

    // source has binding terms in the range [0..<size]; whilst it's possible not all will exist
    //  locally (e.g. mutable context with deleted terms), this is typically not far off, so the size of space wasted
    //  is limited (especially compared to the extra overhead incurred from a hash table structure)
    private val lut = IntArray(source.size) { encoded ->
        val decoded = source.decode(encoded)
            // sentinel for 'unmappable term': was removed from the source context
            ?: return@IntArray Int.MIN_VALUE
        target.encode(decoded)
            // sentinel for 'unmappable term': was not/never a part of the target context
            ?: return@IntArray Int.MIN_VALUE
    }

    /**
     * Reencodes the incoming [quad], which uses the `source` [EncodingContext], into a new [EncodedQuad] associated
     *  with the `target` [EncodingContext], or `null` if the `target` context does not contain the required encoding.
     */
    fun reencode(quad: EncodedQuad): EncodedQuad? {
        // no bounds checks should be required if the contract above is satisfied
        val s = lut[quad.s]
        // we check for MIN_VALUE, as that would mean that the target does not support the new mapping
        if (s == Int.MIN_VALUE) {
            return null
        }

        val p = lut[quad.p]
        if (p == Int.MIN_VALUE) {
            return null
        }
        val o = lut[quad.o]
        if (o == Int.MIN_VALUE) {
            return null
        }

        val g = lut[quad.g]
        if (g == Int.MIN_VALUE) {
            return null
        }

        return EncodedQuad(s, p, o, g)
    }

    /**
     * Compares equality between the [source] quad encoded using the `source` [EncodingContext] used to construct this
     *  mapper with the [target] quad encoded using the `target` [EncodingContext].
     */
    fun equals(source: EncodedQuad, target: EncodedQuad): Boolean {
        // no bounds checks should be required if the contract above is satisfied
        val s = lut[source.s]
        // we check for MIN_VALUE too, as it's technically possible for the target quad to have MIN_VALUE set from
        //  outside the store; this check makes sure that two MIN_VALUEs are not considered equal as we have no
        //  guarantee that it would end up having the same value
        if (s == Int.MIN_VALUE || s != target.s) {
            return false
        }

        val p = lut[source.p]
        if (p == Int.MIN_VALUE || p != target.p) {
            return false
        }

        val o = lut[source.o]
        if (o == Int.MIN_VALUE || o != target.o) {
            return false
        }

        val g = lut[source.g]
        return g != Int.MIN_VALUE && g == target.g
    }

}
