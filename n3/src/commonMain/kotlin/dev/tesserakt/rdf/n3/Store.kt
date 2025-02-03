package dev.tesserakt.rdf.n3

import kotlin.jvm.JvmInline

@ExperimentalN3Api
@JvmInline
value class Store private constructor(private val data: MutableSet<Quad> = mutableSetOf()): Set<Quad> by data {

    constructor(elements: Collection<Quad>): this(data = elements.toMutableSet())

    constructor(): this(mutableSetOf())

    fun add(quad: Quad) {
        data.add(quad)
    }

    override fun toString() = data.toString()

}
