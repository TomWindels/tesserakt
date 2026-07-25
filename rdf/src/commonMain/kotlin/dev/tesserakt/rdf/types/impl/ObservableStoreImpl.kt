package dev.tesserakt.rdf.types.impl

import dev.tesserakt.rdf.types.*

internal class ObservableStoreImpl(quads: Collection<Quad> = emptyList()): AbstractStore(), ObservableStore {

    private val inner = MutableStoreImpl(quads)

    private val listeners = mutableListOf<ObservableStore.Listener>()

    override val context: MutableEncodingContext
        get() = inner.context

    override val size: Int
        get() = inner.size

    override fun iterator() = inner.iterator()

    override fun encodedIterator() = inner.encodedIterator()

    override fun iter(s: Quad.Subject?, p: Quad.Predicate?, o: Quad.Object?, g: Quad.Graph?): Iterator<Quad> {
        return inner.iter(s, p, o, g)
    }

    override fun encodedIter(
        s: EncodedQuadElement,
        p: EncodedQuadElement,
        o: EncodedQuadElement,
        g: EncodedQuadElement
    ): Iterator<EncodedQuad> {
        return inner.encodedIter(s, p, o, g)
    }

    override fun encodedIter(
        s: Quad.Subject?,
        p: Quad.Predicate?,
        o: Quad.Object?,
        g: Quad.Graph?
    ): Iterator<EncodedQuad> {
        return inner.encodedIter(s, p, o, g)
    }

    override fun isEmpty(): Boolean {
        return inner.isEmpty()
    }

    override fun containsAll(elements: Collection<Quad>): Boolean {
        return inner.containsAll(elements)
    }

    override fun contains(element: Quad): Boolean {
        return inner.contains(element)
    }

    override fun add(element: Quad): Boolean {
        // we do the lookup here, so we only have to do it once to both add the value and use it in the callback
        val encoded = EncodedQuad(context, element)
        return if (inner.add(encoded)) {
            listeners.forEach {
                try {
                    it.onQuadAdded(element)
                    it.onQuadAddedEncoded(encoded)
                } catch (e: Throwable) {
                    // TODO: maybe rollback for local data and other listeners?
                    // TODO: better exception type, or return a result type?
                    throw RuntimeException("Failed to add `$element`", e)
                }
            }
            true
        } else {
            false
        }
    }

    override fun remove(element: Quad): Boolean {
        // we do the lookup here for two reasons:
        // * we only have to do it once (the underlying store does not have to encode it again)
        // * the representation is not altered because of the removal (in case encoding contexts would support the
        //  deletion of unused terms in the future)
        val encoded = EncodedQuad(context, element)
        return if (inner.remove(encoded)) {
            listeners.forEach {
                try {
                    it.onQuadRemoved(element)
                    it.onQuadRemovedEncoded(encoded)
                } catch (e: Throwable) {
                    // TODO: maybe rollback for local data and other listeners?
                    // TODO: better exception type, or return a result type?
                    throw RuntimeException("Failed to remove `$element`", e)
                }
            }
            true
        } else {
            false
        }
    }

    override fun clear() {
        inner.pairIterator().forEach { (encoded, quad) ->
            listeners.forEach {
                it.onQuadRemoved(quad)
                it.onQuadRemovedEncoded(encoded)
            }
        }
        inner.clear()
    }

    override fun retainAll(elements: Collection<Quad>): Boolean {
        val targets = elements.toSet()
        val iter = iterator()
        var result = false
        while (iter.hasNext()) {
            if (iter.next() !in targets) {
                result = true
                iter.remove()
            }
        }
        return result
    }

    override fun addListener(listener: ObservableStore.Listener) {
        listeners.add(listener)
    }

    override fun removeListener(listener: ObservableStore.Listener) {
        listeners.remove(listener)
    }

}
