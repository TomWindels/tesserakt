package dev.tesserakt.sparql.runtime.incremental.stream

internal inline fun <T> emptyIterator(): Iterator<T> = EmptyStream.Iterator

internal inline fun <T> emptyIterable(): Iterable<T> = EmptyStream

@Suppress("UNCHECKED_CAST")
internal inline fun <T: Any> emptyStream(): OptimisedStream<T> = EmptyStream as OptimisedStream<T>

internal inline fun <E: Any> streamOf(): OptimisedStream<E> = emptyStream()

internal inline fun <E: Any> streamOf(element: E) =
    SingleStream(element)

internal inline fun <E: Any> streamOf(element1: E, element2: E, vararg others: E) =
    CollectedStream(listOf(element1, element2, *others))

internal inline fun <E: Any> Collection<E>.toStream(): OptimisedStream<E> = when {
    isEmpty() -> emptyStream()
    size == 1 -> SingleStream(single())
    this is List<E> -> CollectedStream(this)
    else -> CollectedStream(toList())
}

internal inline fun <E: Any> Stream<E>.isNotEmpty() = !isEmpty()
