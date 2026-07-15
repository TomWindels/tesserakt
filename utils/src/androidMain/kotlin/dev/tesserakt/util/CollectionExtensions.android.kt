package dev.tesserakt.util

import android.os.Build

/**
 * Replaces the value associated with [key] with the value computed by [transform]ing the original value (if any)
 */
actual inline fun <K, V> MutableMap<K, V>.replace(key: K, crossinline transform: (V?) -> V?) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        compute(key) { _, v -> transform(v) }
    } else {
        val mapped = transform(this[key])
        if (mapped == null) {
            remove(key)
        } else {
            this[key] = mapped
        }
    }
}

/**
 * A custom version of 'stdlib's `getOrPut`, so the lookup is only done once on supported platforms (e.g. JVM).
 */
actual inline fun <K, V> MutableMap<K, V>.getOrInsert(key: K, crossinline default: () -> V): V {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        computeIfAbsent(key) { _ -> default() }
    } else {
        val existing = this[key]
        if (existing != null) {
            return existing
        }
        val value = default()
        this[key] = value
        value
    }
}

/**
 * A custom implementation of the [MutableList.removeFirst] method, implemented to make sure it resolves properly on
 *  Android 14 and below
 */
actual inline fun <T> MutableList<T>.removeFirstElement(): T {
    return removeAt(0)
}

/**
 * A custom implementation of the [MutableList.removeLast] method, implemented to make sure it resolves properly on
 *  Android 14 and below
 */
actual inline fun <T> MutableList<T>.removeLastElement(): T {
    return removeAt(size - 1)
}
