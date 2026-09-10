package dev.tesserakt.concurrent

expect fun <T> ConcurrentSet(): MutableSet<T>

expect fun <T> ConcurrentSet(initialCapacity: Int): MutableSet<T>
