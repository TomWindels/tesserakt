package dev.tesserakt.util

actual inline fun <K, V> MutableMap<K, V>.replace(key: K, crossinline transform: (V?) -> V) {
    this[key] = transform(this[key])
}
