package dev.tesserakt.concurrent

actual fun <T> ConcurrentSet(): MutableSet<T> {
    return HashSet()
}

actual fun <T> ConcurrentSet(initialCapacity: Int): MutableSet<T> {
    return HashSet(initialCapacity = initialCapacity)
}
