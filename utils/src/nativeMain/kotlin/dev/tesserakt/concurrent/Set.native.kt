package dev.tesserakt.concurrent

actual fun <T> ConcurrentSet(): MutableSet<T> {
    throw UnsupportedOperationException("Concurrency support is missing in Kotlin/Native!")
}

actual fun <T> ConcurrentSet(initialCapacity: Int): MutableSet<T> {
    throw UnsupportedOperationException("Concurrency support is missing in Kotlin/Native!")
}
