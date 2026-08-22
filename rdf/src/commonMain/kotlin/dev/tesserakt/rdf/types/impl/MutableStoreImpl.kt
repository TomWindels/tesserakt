package dev.tesserakt.rdf.types.impl

import dev.tesserakt.rdf.types.EncodedQuad
import dev.tesserakt.rdf.types.MutableEncodingContext
import dev.tesserakt.rdf.types.MutableStore
import dev.tesserakt.rdf.types.Quad

internal class MutableStoreImpl: AbstractStore, MutableStore {

    // we first create our encoding context; without any initial state
    override val context: MutableEncodingContext

    private val quads: MutableSet<EncodedQuad>

    override val size: Int
        get() = quads.size

    constructor() {
        context = MutableEncodingContextImpl()
        quads = mutableSetOf()
    }

    constructor(quads: Collection<Quad>) {
        this.context = MutableEncodingContextImpl()
        this.quads = HashSet(quads.size)
        quads.forEach { quad ->
            // we are guaranteed to be able to encode it as we're a mutable collection
            val encoded = EncodedQuad(context, quad)
            this.quads.add(encoded)
        }
    }

    constructor(capacity: Int) {
        this.context = MutableEncodingContextImpl()
        this.quads = HashSet(capacity)
    }

    override fun iterator() = MutableDecodingIterator(src = quads.iterator(), context = context)

    override fun encodedIterator(): MutableIterator<EncodedQuad> = quads.iterator()

    internal fun pairIterator() = MutableDecodingPairIterator(src = quads.iterator(), context = context)

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

    // internal way of directly adding the encoded representation of a quad
    // it is assumed the context used to encode the quad is the same as the context of this store
    fun add(element: EncodedQuad): Boolean {
        return quads.add(element)
    }

    override fun remove(element: Quad): Boolean {
        // we don't want to 'pollute' our context with quad elements not present in our active context; if it's not in
        //  there, the encoded version can't be in there either
        val encoded = EncodedQuad(context.asReadOnlyEncodingContext(), element)
            ?: return false
        return quads.remove(encoded)
    }

    // internal way of directly removing the encoded representation of a quad
    // it is assumed the context used to encode the quad is the same as the context of this store
    fun remove(element: EncodedQuad): Boolean {
        return quads.remove(element)
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
        // we keep the context alive, as this implementation is reused in the observable store implementation,
        //  which still requires it to emit binding changes in the deferred evaluation variant
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
