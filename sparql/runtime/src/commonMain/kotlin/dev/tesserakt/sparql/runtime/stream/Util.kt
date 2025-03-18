package dev.tesserakt.sparql.runtime.stream

import dev.tesserakt.sparql.util.Cardinality

inline fun <T> emptyIterator(): Iterator<T> = EmptyStream.Iterator

inline fun <T> emptyIterable(): Iterable<T> = EmptyStream

@Suppress("UNCHECKED_CAST")
inline fun <T: Any> emptyStream(): OptimisedStream<T> = EmptyStream as OptimisedStream<T>

inline fun <E: Any> streamOf(): OptimisedStream<E> = emptyStream()

inline fun <E: Any> streamOf(element: E) =
    SingleStream(element)

inline fun <E: Any> streamOf(element1: E, element2: E, vararg others: E) =
    CollectedStream(listOf(element1, element2, *others))

inline fun <E: Any> Iterable<E>.toStream(cardinality: Number) = toStream(Cardinality(cardinality))

inline fun <E: Any> Iterable<E>.toStream(cardinality: Cardinality) = object: Stream<E> {

    override val description: String
        get() = "Iterable from ${this@toStream}"

    override val cardinality = cardinality

    override fun supportsEfficientIteration(): Boolean {
        // should be valid
        return true
    }

    override fun iterator(): Iterator<E> {
        return this@toStream.iterator()
    }

    override fun supportsReuse(): Boolean {
        return true
    }

}

inline fun <E: Any> Collection<E>.toStream(): OptimisedStream<E> = when {
    isEmpty() -> emptyStream()
    size == 1 -> SingleStream(single())
    this is List<E> -> CollectedStream(this)
    else -> CollectedStream(toList())
}

inline fun <E: Any> List<E>.toStream(): CollectedStream<E> = CollectedStream(this)
