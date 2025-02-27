package dev.tesserakt.sparql.runtime.incremental.stream

internal class StreamTransformNullable<I: Any, O: Any>(
    private val source: OptimisedStream<I>,
    private val transform: (I) -> Stream<O>?
): Stream<O> {

    private class Iter<I, O: Any>(
        private var active: Iterator<O>,
        private val source: Iterator<I>,
        private val transform: (I) -> Stream<O>?,
    ): Iterator<O> {

        private var next: O? = null

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
                active = transform(source.next())?.iterator() ?: continue
            }
            if (active.hasNext()) {
                return active.next()
            }
            return null
        }

    }

    override val cardinality: Int = Int.MAX_VALUE

    // there's no better way here
    private val _isEmpty by lazy { !iterator().hasNext() }

    init {
        require(source.isNotEmpty())
    }

    override fun isEmpty() = _isEmpty

    override fun supportsEfficientIteration(): Boolean {
        return false
    }

    override fun iterator(): Iterator<O> {
        // finding the first non-null stream iterator; we can't cache this result as its associated iterator is
        //  not cloneable across multiple `iterator` calls
        val iter = source.iterator()
        var stream = transform(iter.next())?.iterator()
        while (stream == null && iter.hasNext()) {
            stream = transform(iter.next())?.iterator()
        }
        return if (stream == null) emptyIterator() else Iter(stream, iter, transform)
    }

}
