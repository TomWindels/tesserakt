package dev.tesserakt.util

actual inline fun <K, V> MutableMap<K, V>.replace(key: K, crossinline transform: (V?) -> V?) {
    val mapped = transform(this[key])
    if (mapped == null) {
        remove(key)
    } else {
        this[key] = mapped
    }
}

/**
 * A custom version of 'stdlib's `getOrPut`, so the lookup is only done once on supported platforms (e.g. JVM).
 */
actual inline fun <K, V> MutableMap<K, V>.getOrInsert(key: K, crossinline default: () -> V): V {
    return this.getOrPut(key, defaultValue = default)
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

actual inline fun IntArray.cloneTo(
    target: IntArray,
    thisOffset: Int,
    targetOffset: Int,
    length: Int
) {
    var i = 0
    while (i < length) {
        target[i + targetOffset] = this[i + thisOffset]
        ++i
    }
}
