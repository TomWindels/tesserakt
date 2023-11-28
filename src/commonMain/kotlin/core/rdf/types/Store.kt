package core.rdf.types

import kotlin.jvm.JvmStatic

class Store: TripleSource() {

    inner class Filter internal constructor(
        private val filter: (Triple) -> Boolean
    ): Iterator<Triple> {

        private var i = find(0, filter)

        override fun next() = triples[i].also { i = find(start = i + 1, filter = filter) }

        override fun hasNext() = i != -1

    }

    // TODO: actually performant implementation
    private val triples = mutableListOf<Triple>()

    fun addAll(triple: Iterable<Triple>) {
        triples.addAll(triple)
    }

    fun addAll(triple: Collection<Triple>) {
        triples.addAll(triple)
    }

    fun add(triple: Triple) {
        triples.add(triple)
    }

    fun contains(subject: Triple.Term?, predicate: Triple.Term?, `object`: Triple.Term?): Boolean {
        return find(0, buildFilter(subject, predicate, `object`)) != -1
    }

    override fun filter(
        subject: Triple.Term?,
        predicate: Triple.Term?,
        `object`: Triple.Term?
    ): Iterator<Triple> =
        if (subject == null && predicate == null && `object` == null) {
            triples.iterator()
        } else {
            Filter(buildFilter(subject, predicate, `object`))
        }

    override fun toString() = triples.toString()

    /* implementation details */

    /** Finds the next index (starting at `start`) matching the criteria, returns -1 if none are found **/
    private inline fun find(start: Int = 0, filter: (Triple) -> Boolean): Int {
        var i = start
        while (i < triples.size) {
            if (filter(triples[i])) {
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
            subject: Triple.Term?,
            predicate: Triple.Term?,
            `object`: Triple.Term?
        ): (Triple) -> Boolean = when {
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
