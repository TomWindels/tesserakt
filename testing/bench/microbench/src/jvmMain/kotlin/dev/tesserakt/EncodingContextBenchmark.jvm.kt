package dev.tesserakt

import dev.tesserakt.concurrent.ConcurrencyMode
import dev.tesserakt.concurrent.MultiThreaded
import dev.tesserakt.concurrent.SingleThreaded
import dev.tesserakt.concurrent.set
import java.util.concurrent.ConcurrentHashMap

actual inline fun withMultithreading(block: () -> Unit) {
    ConcurrencyMode.set(MultiThreaded)
    block()
    ConcurrencyMode.set(SingleThreaded)
}

actual fun <T> concurrentSet(): MutableSet<T> {
    return ConcurrentHashMap.newKeySet<T>()
}
