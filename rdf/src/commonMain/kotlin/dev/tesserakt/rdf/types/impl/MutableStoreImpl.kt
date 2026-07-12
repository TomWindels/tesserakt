package dev.tesserakt.rdf.types.impl

import dev.tesserakt.rdf.types.EncodedQuad
import dev.tesserakt.rdf.types.MutableStore
import dev.tesserakt.rdf.types.Quad

internal class MutableStoreImpl(quads: Collection<Quad> = emptyList()): AbstractStore(), MutableStore {

    // we first create our encoding context; without any initial state
    override val context = MutableEncodingContextImpl()

    // then we create our actual set of *encoded* quads
    // the fact they're encoded makes checking for 'contains' etc. faster, as we mainly pay the price for initial lookup
    //  of the individual terms, and can then compare the encoded variants faster
    private val quads = run {
        val result = mutableSetOf<EncodedQuad>()
        quads.forEach { quad ->
            // we are guaranteed to be able to encode it as we're a mutable collection
            val encoded = EncodedQuad(context, quad)
            result.add(encoded)
        }
        result
    }

    override val size: Int
        get() = quads.size

    override fun iterator() = MutableDecodingIterator(src = quads.iterator(), context = context)

    override fun encodedIterator(): MutableIterator<EncodedQuad> = quads.iterator()

    override fun isEmpty(): Boolean {
        return quads.isEmpty()
    }

    override fun containsAll(elements: Collection<Quad>): Boolean {
        return elements.all { this.contains(it) }
    }

    override fun contains(element: Quad): Boolean {
        // two possible scenarios where we don't contain a given quad:
        // * either our immutable context doesn't have it, in which case we don't contain a quad element this quad uses,
        //  and thus cannot possibly contain the quad, or
        // * we have all individual quad elements present in our context, but not in this
        //  specific 'configuration' (s, p, o and g)
        val encoded = EncodedQuad(context.asReadOnlyEncodingContext(), element)
            // case 1: the quad has an element we don't even have an encoded representation for; so we don't have the
            //  quad itself either
            ?: return false
        // case 2: we have to check the encoded representation in our collection
        return quads.contains(encoded)
    }

    override fun add(element: Quad): Boolean {
        return quads.add(EncodedQuad(context, element))
    }

    override fun remove(element: Quad): Boolean {
        // we don't want to 'pollute' our context with quad elements not present in our active context; if it's not in
        //  there, the encoded version can't be in there either
        val encoded = EncodedQuad(context.asReadOnlyEncodingContext(), element)
            ?: return false
        return quads.remove(encoded)
    }

    override fun addAll(elements: Collection<Quad>): Boolean {
        var done = false
        elements.forEach { element ->
            // making sure lazy evaluation doesn't mean only 1 new element gets inserted
            done = add(element) || done
        }
        return done
    }

    override fun clear() {
        quads.clear()
    }

    override fun removeAll(elements: Collection<Quad>): Boolean {
        // TODO(perf): not sure if the creation of an intermediate set containing 'to be removed quads' is faster than
        //  simply 'spamming' the `remove()` method
        val ctx = context.asReadOnlyEncodingContext()
        val encoded = elements
            .mapNotNullTo(mutableSetOf()) { EncodedQuad(ctx, it) }
        return quads.removeAll(encoded)
    }

    override fun retainAll(elements: Collection<Quad>): Boolean {
        val ctx = context.asReadOnlyEncodingContext()
        val encoded = elements
            .mapNotNullTo(mutableSetOf()) { EncodedQuad(ctx, it) }
        return quads.retainAll(encoded)
    }

}
