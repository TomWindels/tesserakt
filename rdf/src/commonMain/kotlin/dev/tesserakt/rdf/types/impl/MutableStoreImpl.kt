package dev.tesserakt.rdf.types.impl

import dev.tesserakt.rdf.types.MutableStore
import dev.tesserakt.rdf.types.Quad

internal class MutableStoreImpl(quads: Collection<Quad> = emptyList()): AbstractStore(), MutableStore {

    // stored quads utilize set semantics, duplicates are not allowed
    private val quads = quads.toMutableSet()

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

    override fun add(element: Quad): Boolean {
        return quads.add(element)
    }

    override fun remove(element: Quad): Boolean {
        return quads.remove(element)
    }

    override fun addAll(elements: Collection<Quad>): Boolean {
        return quads.addAll(elements)
    }

    override fun clear() {
        quads.clear()
    }

    override fun removeAll(elements: Collection<Quad>): Boolean {
        return quads.removeAll(elements.toSet())
    }

    override fun retainAll(elements: Collection<Quad>): Boolean {
        return quads.retainAll(elements.toSet())
    }

}
