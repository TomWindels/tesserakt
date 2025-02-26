package dev.tesserakt.sparql.runtime.incremental.stream

internal inline fun <T> emptyIterator(): Iterator<T> = EmptyStream.Iterator

internal inline fun <T> emptyIterable(): Iterable<T> = EmptyStream

@Suppress("UNCHECKED_CAST")
internal inline fun <T: Any> emptyStream(): Stream<T> = EmptyStream as Stream<T>

internal inline fun <E: Any> streamOf(): Stream<E> = emptyStream()

internal inline fun <E: Any> streamOf(element: E) =
    SingleStream(element)

internal inline fun <E: Any> streamOf(element1: E, element2: E, vararg others: E) =
    CollectedStream(listOf(element1, element2, *others))

internal inline fun <E: Any> Collection<E>.toStream(): Stream<E> = when {
    isEmpty() -> emptyStream()
    size == 1 -> SingleStream(single())
    this is List<E> -> CollectedStream(this)
    else -> CollectedStream(toList())
}

internal inline fun <E: Any> Stream<E>.isNotEmpty() = !isEmpty()
