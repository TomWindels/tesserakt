package dev.tesserakt.types

suspend inline fun <T> SuspendingIterator<T>.forEach(consumer: (T) -> Unit) {
    while (hasNext()) {
        consumer(next())
    }
}
