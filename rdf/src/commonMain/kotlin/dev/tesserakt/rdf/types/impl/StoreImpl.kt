package dev.tesserakt.rdf.types.impl

import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.rdf.types.Store

internal class StoreImpl(data: Collection<Quad>): AbstractStore() {

    // stored quads utilize set semantics, duplicates are not allowed
    private val quads = data.toSet()
    // considering the contents don't change, we can cache the collection's hash code
    private val hashCode by lazy {
        var result = 0
        quads.forEach { quad -> result += quad.hashCode() }
        result
    }

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

    override fun hashCode(): Int {
        return hashCode
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is Store) {
            return false
        }
        return this.size == other.size && containsAll(other)
    }

}
