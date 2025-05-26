package dev.tesserakt.util

inline fun <T> Iterator<T>.singleOrNull(): T? {
    if (!hasNext()) {
        return null
    }
    val result = next()
    return result.takeIf { !hasNext() }
}

inline fun <T> Iterator<T>.single(): T {
    if (!hasNext()) {
        throw NoSuchElementException()
    }
    val result = next()
    if (hasNext()) {
        throw IllegalStateException()
    }
    return result
}

inline fun <T, R, C: MutableCollection<in R>> Iterator<T>.mapTo(collection: C, transform: (T) -> R): C {
    forEach { collection.add(transform(it)) }
    return collection
}

inline fun <T, R> Iterator<T>.map(transform: (T) -> R): List<R> {
    val result = mutableListOf<R>()
    forEach { result.add(transform(it)) }
    return result
}
