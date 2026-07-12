package dev.tesserakt.util

/**
 * Replaces the value associated with [key] with the value computed by [transform]ing the original value (if any)
 */
actual inline fun <K, V> MutableMap<K, V>.replace(key: K, crossinline transform: (V?) -> V?) {
    compute(key) { _, v -> transform(v) }
}

/**
 * A custom version of 'stdlib's `getOrPut`, so the lookup is only done once on supported platforms (e.g. JVM).
 */
actual inline fun <K, V> MutableMap<K, V>.getOrInsert(key: K, crossinline default: () -> V): V {
    return computeIfAbsent(key) { _ -> default() }
}

/**
 * A custom implementation of the [MutableList.removeFirst] method, implemented to make sure it resolves properly on
 *  Android 14 and below
 */
actual inline fun <T> MutableList<T>.removeFirstElement(): T {
    return removeFirst()
}

/**
 * A custom implementation of the [MutableList.removeLast] method, implemented to make sure it resolves properly on
 *  Android 14 and below
 */
actual inline fun <T> MutableList<T>.removeLastElement(): T {
    return removeLast()
}
