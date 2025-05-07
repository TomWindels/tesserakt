package dev.tesserakt.util

actual inline fun <K, V: Any> MutableMap<K, V>.replace(key: K, crossinline transform: (V?) -> V?) {
    val new = transform(this[key])
    if (new == null) {
        this.remove(key)
    } else {
        this[key] = new
    }
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
