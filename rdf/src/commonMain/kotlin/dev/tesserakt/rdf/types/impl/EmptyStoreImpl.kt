package dev.tesserakt.rdf.types.impl

import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.rdf.types.Store

internal object EmptyStoreImpl : Store {

    override val size: Int
        get() = 0

    override fun contains(element: Quad) = false

    override fun containsAll(elements: Collection<Quad>) = false

    override fun isEmpty() = true

    override fun iterator(): Iterator<Quad> = emptyIterator

    override fun iter(s: Quad.Subject?, p: Quad.Predicate?, o: Quad.Object?, g: Quad.Graph?) = emptyIterator

    override fun toString(): String = "<empty store>"

    override fun hashCode() = 0

}

private val emptyIterator = emptyList<Quad>().iterator()
