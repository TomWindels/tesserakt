package dev.tesserakt.sparql.util

/**
 * A helper type to transform an [inner] collection of type [T] into another collection [R] using a lazy transform:
 *  only upon iteration are elements transformed using the provided [transform] method, instead of eagerly using
 *  the [Iterable.map] function, making it possible to avoid unnecessary overhead when followed by subsequent operations,
 *  such as [Iterable.take] or [Iterable.first].
 *
 * The collection does not cache transformed data, as it assumes the [inner] collection to be volatile (as otherwise
 *  the use of [Iterable.map] with result caching would be a better alternative).
 *
 * While this collection implements the [List] interface, most methods are not supported as it would require many
 *  [transform] invocations. Instead, the [List] interface is used to more efficiently obtain direct indexing using the
 *  [get] operator, avoiding the [transform] of other unrequested items.
 *
 * NOTE: due to the one-way nature of [transform], [contains] and [containsAll] is not implemented as its evaluation
 *  would require complete transformation to occur anyway!
 */
class MappedCollection<T, R>(
    private val inner: Collection<T>,
    private val transform: (T) -> R,
) : List<R> {

    override fun get(index: Int): R {
        // automatically checks whether `inner` is a list itself, allowing direct access into the inner collection
        //  as well, further limiting overhead
        val ele = inner.elementAt(index)
        return transform(ele)
    }

    override val size: Int
        get() = inner.size

    override fun isEmpty(): Boolean = inner.isEmpty()

    override fun iterator(): Iterator<R> = object: Iterator<R> {
        private val iter = inner.iterator()

        override fun hasNext(): Boolean {
            return iter.hasNext()
        }

        override fun next(): R {
            return transform(iter.next())
        }
    }

    /* unsupported List methods */

    override fun listIterator(): ListIterator<R> {
        throw UnsupportedOperationException()
    }

    override fun listIterator(index: Int): ListIterator<R> {
        throw UnsupportedOperationException()
    }

    override fun subList(fromIndex: Int, toIndex: Int): List<R> {
        throw UnsupportedOperationException()
    }

    override fun indexOf(element: R): Int {
        throw UnsupportedOperationException()
    }

    override fun lastIndexOf(element: R): Int {
        throw NoSuchElementException()
    }

    /* unsupported Collection methods */

    override fun contains(element: R): Boolean {
        throw UnsupportedOperationException()
    }

    override fun containsAll(elements: Collection<R>): Boolean {
        throw UnsupportedOperationException()
    }

    companion object {

        fun <T, R> Collection<T>.mapLazily(transform: (T) -> R) = MappedCollection(inner = this, transform = transform)

    }

}
