package dev.tesserakt.util

/**
 * Replaces the value associated with [key] with the value computed by [transform]ing the original value (if any)
 */
actual inline fun <K, V: Any> MutableMap<K, V>.replace(key: K, crossinline transform: (V?) -> V?) {
    // the `compute` method is only available since Android SDK 24; as we currently have no `minSdk` configured for all
    //  Android modules, we should fall back on this approach instead
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
    return removeAt(0)
}

/**
 * A custom implementation of the [MutableList.removeLast] method, implemented to make sure it resolves properly on
 *  Android 14 and below
 */
actual inline fun <T> MutableList<T>.removeLastElement(): T {
    return removeAt(size - 1)
}
