package dev.tesserakt.util

inline fun <T> Iterator<T>.singleOrNull(): T? {
    if (!hasNext()) {
        return null
    }
    val result = next()
    return result.takeIf { !hasNext() }
}

inline fun <T, R, C: MutableCollection<in R>> Iterator<T>.mapTo(collection: C, transform: (T) -> R): C {
    forEach { collection.add(transform(it)) }
    return collection
}
