@file:Suppress("unused")

package dev.tesserakt.rdf.types

import dev.tesserakt.util.fit

// TODO: make fully immutable

abstract class Store {

    // TODO: actually performant implementation
    // stored quads utilize set semantics, duplicates are not allowed
    internal abstract val quads: Set<Quad>

    open val size: Int
        get() = quads.size

    open fun isEmpty(): Boolean {
        return quads.isEmpty()
    }

    open fun containsAll(elements: Collection<Quad>): Boolean {
        return quads.containsAll(elements)
    }

    open fun contains(element: Quad): Boolean {
        return quads.contains(element)
    }

    open fun forEach(block: (Quad) -> Unit) {
        quads.forEach(block)
    }

    open fun toSet() = quads

    open fun first(): Quad = quads.first()

    open fun last(): Quad = quads.last()

    /**
     * A special variant of the [forEach] method, capable of halting execution prematurely when [block] returns `true`.
     * The behaviour is identical to the regular [forEach] method when always returning `false`.
     */
    open fun forEachUntil(block: (Quad) -> Boolean) {
        quads.forEach {
            if (block(it)) {
                return
            }
        }
    }

    override fun toString() = if (isEmpty()) "<empty store>" else buildString {
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
