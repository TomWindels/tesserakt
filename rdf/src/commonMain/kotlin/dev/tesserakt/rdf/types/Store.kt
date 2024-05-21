@file:Suppress("unused")

package dev.tesserakt.rdf.types

import kotlin.jvm.JvmStatic

class Store: QuadSource() {

    inner class Filter internal constructor(
        private val filter: (Quad) -> Boolean
    ): Iterator<Quad> {

        private var i = find(0, filter)

        override fun next() = quads[i].also { i = find(start = i + 1, filter = filter) }

        override fun hasNext() = i != -1

    }

    // TODO: actually performant implementation
    private val quads = mutableListOf<Quad>()

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

    fun contains(subject: Quad.Term?, predicate: Quad.Term?, `object`: Quad.Term?): Boolean {
        return find(0, buildFilter(subject, predicate, `object`)) != -1
    }

    override fun filter(
        subject: Quad.Term?,
        predicate: Quad.Term?,
        `object`: Quad.Term?
    ): Iterator<Quad> =
        if (subject == null && predicate == null && `object` == null) {
            quads.iterator()
        } else {
            Filter(buildFilter(subject, predicate, `object`))
        }

    override fun toString() = quads.toString()

    /* implementation details */

    /** Finds the next index (starting at `start`) matching the criteria, returns -1 if none are found **/
    private inline fun find(start: Int = 0, filter: (Quad) -> Boolean): Int {
        var i = start
        while (i < quads.size) {
            if (filter(quads[i])) {
                return i
            }
            ++i
        }
        return -1
    }


    companion object {

        /* implementation details */
        @JvmStatic
        internal fun buildFilter(
            subject: Quad.Term?,
            predicate: Quad.Term?,
            `object`: Quad.Term?
        ): (Quad) -> Boolean = when {
            subject != null && predicate != null && `object` != null ->
                { { it.s == subject && it.p == predicate && it.o == `object` } }
            subject != null && predicate != null ->
                { { it.s == subject && it.p == predicate } }
            subject != null && `object` != null ->
                { { it.s == subject && it.o == `object` } }
            predicate != null && `object` != null ->
                { { it.p == predicate && it.o == `object` } }
            subject != null ->
                { { it.s == subject } }
            `object` != null ->
                { { it.o == `object` } }
            predicate != null ->
                { { it.p == predicate } }
            else -> { { true } }
        }

    }

}
