package dev.tesserakt.concurrent

import java.util.concurrent.ConcurrentHashMap

actual fun <T> ConcurrentSet(): MutableSet<T> {
    return ConcurrentHashMap.newKeySet<T>()
}

actual fun <T> ConcurrentSet(initialCapacity: Int): MutableSet<T> {
    return ConcurrentHashMap.newKeySet<T>(initialCapacity)
}
