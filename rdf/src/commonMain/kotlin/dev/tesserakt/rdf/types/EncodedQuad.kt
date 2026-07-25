package dev.tesserakt.rdf.types

/**
 * An encoded version of the [Quad] type: the term values ([Quad.Element]) for [Quad.s], [Quad.p], [Quad.o]
 *  and [Quad.g] are replaced with numerical values tied to a specific [EncodingContext], such as a [Store], for faster
 *  comparison (e.g. lookup using an index) and querying.
 *
 * Note that using this type without its context is meaningless, and cannot be transferred between [EncodingContext]s
 *  without decoding first.
 */
data class EncodedQuad(
    val s: Int,
    val p: Int,
    val o: Int,
    val g: Int,
) {

    constructor(context: MutableEncodingContext, quad: Quad): this(
        s = context.encode(quad.s),
        p = context.encode(quad.p),
        o = context.encode(quad.o),
        g = context.encode(quad.g),
    )

    override fun hashCode(): Int {
        // generated hash code function
        var result = s
        result = 31 * result + p
        result = 31 * result + o
        result = 31 * result + g
        return result
    }

    override fun equals(other: Any?): Boolean {
        if (other !is EncodedQuad) {
            return false
        }
        return s == other.s && p == other.p && o == other.o && g == other.g
    }

    companion object {

        /**
         * Tries to construct an encoded version of the given [quad] using the provided [context]. If
         *  the [quad] contains a [Quad.Element] that is not part of this [EncodingContext] object, encoding is not
         *  possible, and `null` is returned.
         */
        operator fun invoke(context: EncodingContext, quad: Quad): EncodedQuad? {
            return EncodedQuad(
                s = context.encode(quad.s) ?: return null,
                p = context.encode(quad.p) ?: return null,
                o = context.encode(quad.o) ?: return null,
                g = context.encode(quad.g) ?: return null,
            )
        }
    }

}
