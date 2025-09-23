package dev.tesserakt.rdf.types.impl

import dev.tesserakt.rdf.types.ObservableStore
import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.util.fit

internal class ObservableStoreImpl(quads: Collection<Quad> = emptyList()): AbstractStore(), ObservableStore {

    inner class ObservableIterator : MutableIterator<Quad> {

        private var last: Quad? = null
        private val iter = quads.iterator()

        override fun next(): Quad {
            val result = iter.next()
            last = result
            return result
        }

        override fun hasNext(): Boolean {
            return iter.hasNext()
        }

        override fun remove() {
            // matching other `MutableIterator` implementations' behaviour
            val removed = last ?: throw IllegalStateException()
            last = null
            listeners.forEach { it.onQuadRemoved(removed) }
            iter.remove()
        }

    }

    // stored quads utilize set semantics, duplicates are not allowed
    private val quads = quads.toMutableSet()

    private val listeners = mutableListOf<ObservableStore.Listener>()

    override val size: Int
        get() = quads.size

    override fun iterator() = ObservableIterator()

    override fun isEmpty(): Boolean {
        return quads.isEmpty()
    }

    override fun containsAll(elements: Collection<Quad>): Boolean {
        return quads.containsAll(elements)
    }

    override fun contains(element: Quad): Boolean {
        return quads.contains(element)
    }

    override fun add(element: Quad): Boolean {
        return if (quads.add(element)) {
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
        return if (quads.remove(element)) {
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
        quads.forEach { quad ->
            listeners.forEach {
                it.onQuadRemoved(quad)
            }
        }
        quads.clear()
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

    override fun toString() = if (isEmpty()) "Empty store" else buildString {
        val s = quads.map { it.s.toString() }
        val p = quads.map { it.p.toString() }
        val o = quads.map { it.o.toString() }

        val sl = s.maxOf { it.length }
        val pl = p.maxOf { it.length }
        val ol = o.maxOf { it.length }

        append("Subject".fit(sl))
        append(" | ")
        append("Predicate".fit(pl))
        append(" | ")
        appendLine("Object".fit(ol))

        repeat(quads.size) { i ->
            append(s[i].padEnd(sl))
            append(" | ")
            append(p[i].padEnd(pl))
            append(" | ")
            appendLine(o[i].padEnd(ol))
        }
    }

}
