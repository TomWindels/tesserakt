@file:Suppress("unused")

package dev.tesserakt.rdf.types

class Store: Iterable<Quad> {

    // TODO: actually performant implementation
    // stored quads utilize set semantics, duplicates are not allowed
    private val quads = mutableSetOf<Quad>()

    override fun iterator() = quads.iterator()

    val size: Int
        get() = quads.size

    fun addAll(quad: Iterable<Quad>) {
        quads.addAll(quad)
    }

    fun addAll(quad: Collection<Quad>) {
        quads.addAll(quad)
    }

    fun add(quad: Quad) {
        quads.add(quad)
    }

    override fun toString() = quads.toString()

}
