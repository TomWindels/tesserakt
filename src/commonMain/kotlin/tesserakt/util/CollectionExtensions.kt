@file:Suppress("NOTHING_TO_INLINE", "unused")

package tesserakt.util

inline fun <T, K, V> Iterable<T>.associateIndexedNotNull(
    mapper: (Int, T) -> Pair<K, V>?
): Map<K, V> = buildMap {
    var i = 0
    for (element in this@associateIndexedNotNull) {
        val (k, v) = mapper(i++, element) ?: continue
        this[k] = v
    }
}

inline fun <T, K, V> Array<T>.associateIndexedNotNull(
    mapper: (Int, T) -> Pair<K, V>?
): Map<K, V> = buildMap {
    var i = 0
    for (element in this@associateIndexedNotNull) {
        val (k, v) = mapper(i++, element) ?: continue
        this[k] = v
    }
}

inline fun <K, V> Iterable<Map<K, V>>.merge() =
    buildMap { this@merge.forEach { putAll(it) } }

/**
 * Returns a new list containing all elements from `this` collection that don't contain different values compared to
 *  the `reference` input. Example
 *  ```kt
 *  val reference = mapOf('a' to 0)
 *  val list = listOf(
 *      mapOf('a' to 1, 'b' to 0),
 *      mapOf('a' to 0, 'b' to 0)
 *  )
 *  list.filterCompatibleWith(reference) // returns [ Map('a' to 0, 'b' to 0) ]
 *  ```
 */
inline fun <K: Any, V: Any> Collection<Map<K, V>>.filterCompatibleWith(reference: Map<K, V>) =
    filterTo(ArrayList(size)) { element -> reference.compatibleWith(element) }

/**
 * Returns a new list containing all elements from `this` collection that don't contain different values compared to
 *  the `reference` input, with the `reference` input guaranteed to be present for every entry. Example
 *  ```kt
 *  val reference = mapOf('a' to 0, 'c' to 3)
 *  val list = listOf(
 *      mapOf('a' to 1, 'b' to 0),
 *      mapOf('a' to 0, 'b' to 0)
 *  )
 *  list.expandCompatibleWith(reference) // returns [ Map('a' to 0, 'b' to 0, 'c' to 3) ]
 *  ```
 */
inline fun <K: Any, V: Any> Collection<Map<K, V>>.expandCompatibleWith(reference: Map<K, V>): List<Map<K, V>> {
    val result = ArrayList<Map<K, V>>(size)
    forEach { element ->
        if (reference.compatibleWith(reference)) {
            result.add(element + reference)
        }
    }
    return result
}

inline fun <K: Any, V: Any> Map<K, V>.compatibleWith(reference: Map<K, V>) =
    reference.all { (refKey, refValue) -> val data = this[refKey]; data == null || data == refValue}
