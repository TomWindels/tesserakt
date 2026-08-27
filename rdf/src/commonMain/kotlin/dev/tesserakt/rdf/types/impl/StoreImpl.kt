package dev.tesserakt.rdf.types.impl

import dev.tesserakt.rdf.types.EncodedQuad
import dev.tesserakt.rdf.types.Quad

// not required here: we optimized hash code as we're readonly, but the equals check stays in place
@Suppress("EqualsOrHashCode")
internal class StoreImpl(data: Collection<Quad>): AbstractStore() {

    private val quads: Set<EncodedQuad>
    override val context: ImmutableEncodingContextImpl

    // considering the contents don't change, we can cache the collection's hash code
    private val hashCode by lazy { super.hashCode() }

    init {
        val quads = HashSet<EncodedQuad>(data.size)
        this.context = ImmutableEncodingContextImpl(data, quads)
        this.quads = quads
    }

    override val size: Int
        get() = quads.size

    override fun encodedIterator(): Iterator<EncodedQuad> = quads.iterator()

    override fun isEmpty(): Boolean {
        return quads.isEmpty()
    }

    override fun contains(element: Quad): Boolean {
        // two possible scenarios where we don't contain a given quad:
        // * either our immutable context doesn't have it, in which case we don't contain a quad element this quad uses,
        //  and thus cannot possibly contain the quad, or
        // * we have all individual quad elements present in our context, but not in this
        //  specific 'configuration' (s, p, o and g)
        val encoded = EncodedQuad(context, element)
            // case 1: the quad has an element we don't even have an encoded representation for; so we don't have the
            //  quad itself either
            ?: return false
        // case 2: we have to check the encoded representation in our collection
        return quads.contains(encoded)
    }

    override fun hashCode(): Int {
        return hashCode
    }

}
