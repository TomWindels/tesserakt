package dev.tesserakt.util

/**
 * Replaces the value associated with [key] with the value computed by [transform]ing the original value (if any)
 */
actual inline fun <K, V> MutableMap<K, V>.replace(key: K, crossinline transform: (V?) -> V) {
    this[key] = transform(this[key])
}
