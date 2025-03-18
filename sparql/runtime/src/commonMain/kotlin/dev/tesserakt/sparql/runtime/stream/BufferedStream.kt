package dev.tesserakt.sparql.runtime.stream

class BufferedStream<E: Any>(
    private val source: Stream<E>,
): Stream<E>, OptimisedStream<E> {

    private val iter = source.iterator()
    private val buf = ArrayList<E>()

    private inner class Iter: Iterator<E> {

        private var i = 0
        private var next: E? = null

        override fun hasNext(): Boolean {
            if (next != null) {
                return true
            }
            next = getNext()
            return next != null
        }

        override fun next(): E {
            val current = next ?: getNext()
            next = null
            return current ?: throw NoSuchElementException("Stream $description has no elements remaining!")
        }

        private fun getNext(): E? {
            if (i < buf.size) {
                return buf[i++]
            }
            if (iter.hasNext()) {
                val result = iter.next()
                ++i
                buf.add(result)
                return result
            }
            return null
        }

    }

    override val description: String
        get() = "Buffered[${source.description}]"

    override val cardinality get() = source.cardinality

    override fun iterator(): Iterator<E> {
        return Iter()
    }

    override fun supportsReuse(): Boolean {
        return true
    }

}
