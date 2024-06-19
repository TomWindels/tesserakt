@file:Suppress("unused")

package dev.tesserakt.rdf.types

import dev.tesserakt.util.fit

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

    override fun toString() = buildString {
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
