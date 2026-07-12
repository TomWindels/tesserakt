package dev.tesserakt.rdf.types.impl

import dev.tesserakt.rdf.types.EncodedQuad
import dev.tesserakt.rdf.types.MutableEncodingContext
import dev.tesserakt.rdf.types.ObservableStore
import dev.tesserakt.rdf.types.Quad

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
        return if (inner.add(element)) {
            listeners.forEach {
                try {
                    it.onQuadAdded(element)
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
        return if (inner.remove(element)) {
            listeners.forEach {
                try {
                    it.onQuadRemoved(element)
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
        inner.forEach { quad ->
            listeners.forEach {
                it.onQuadRemoved(quad)
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
