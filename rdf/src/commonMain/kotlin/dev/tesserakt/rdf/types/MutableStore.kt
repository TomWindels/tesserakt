@file:Suppress("unused")

package dev.tesserakt.rdf.types

import dev.tesserakt.util.fit

class MutableStore(quads: Collection<Quad> = emptyList()): Collection<Quad> {

    interface Listener {
        fun onQuadAdded(quad: Quad)
        fun onQuadRemoved(quad: Quad)
    }

    // TODO: actually performant implementation
    // stored quads utilize set semantics, duplicates are not allowed
    private val quads = quads.toMutableSet()

    private val listeners = mutableListOf<Listener>()

    override val size: Int
        get() = quads.size

    override fun iterator() = quads.iterator()

    override fun isEmpty(): Boolean {
        return quads.isEmpty()
    }

    override fun containsAll(elements: Collection<Quad>): Boolean {
        return quads.containsAll(elements)
    }

    override fun contains(element: Quad): Boolean {
        return quads.contains(element)
    }

    fun add(quad: Quad) {
        if (quads.add(quad)) {
            listeners.forEach {
                try {
                    it.onQuadAdded(quad)
                } catch (e: Throwable) {
                    // TODO: maybe rollback for local data and other listeners?
                    // TODO: better exception type, or return a result type?
                    throw RuntimeException("Failed to add `$quad`", e)
                }
            }
        }
    }

    fun remove(quad: Quad) {
        if (quads.remove(quad)) {
            listeners.forEach {
                try {
                    it.onQuadRemoved(quad)
                } catch (e: Throwable) {
                    // TODO: maybe rollback for local data and other listeners?
                    // TODO: better exception type, or return a result type?
                    throw RuntimeException("Failed to remove `$quad`", e)
                }
            }
        }
    }

    fun addListener(listener: Listener) {
        listeners.add(listener)
    }

    fun removeListener(listener: Listener) {
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
