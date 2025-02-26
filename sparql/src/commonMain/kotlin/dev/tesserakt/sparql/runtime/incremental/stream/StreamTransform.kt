package dev.tesserakt.sparql.runtime.incremental.stream

internal class StreamTransform<I: Any, O: Any>(
    private val source: Stream<I>,
    private val transform: (I) -> Stream<O>
): Stream<O> {

    private class Iter<I, O: Any>(
        private val source: Iterator<I>,
        private val transform: (I) -> Stream<O>,
    ): Iterator<O> {

        private var active = transform(source.next()).iterator()
        private var next = getNext()

        override fun hasNext(): Boolean {
            if (next != null) {
                return true
            }
            next = getNext()
            return next != null
        }

        override fun next(): O {
            val current = next ?: getNext()
            next = null
            return current ?: throw NoSuchElementException()
        }

        private fun getNext(): O? {
            while (!active.hasNext() && source.hasNext()) {
                active = transform(source.next()).iterator()
            }
            if (active.hasNext()) {
                return active.next()
            }
            return null
        }

    }

    override val cardinality: Int by lazy { source.sumOf { transform(it).cardinality } }

    // there's no better way here
    private val _isEmpty by lazy { !iterator().hasNext() }

    override fun isEmpty() = _isEmpty

    init {
        require(source.isNotEmpty())
    }

    override fun iterator(): Iterator<O> {
        return Iter(source.iterator(), transform)
    }

}
