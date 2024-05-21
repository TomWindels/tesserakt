@file:Suppress("NOTHING_TO_INLINE", "unused")

package dev.tesserakt.util

inline fun <T, K, V> Iterable<T>.associateIndexed(
    mapper: (Int, T) -> Pair<K, V>
): Map<K, V> = buildMap {
    var i = 0
    for (element in this@associateIndexed) {
        val (k, v) = mapper(i++, element)
        this[k] = v
    }
}

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
        if (reference.compatibleWith(element)) {
            result.add(element + reference)
        }
    }
    return result
}

inline fun <K: Any, V: Any> Map<K, V>.compatibleWith(reference: Map<K, V>) =
    reference.all { (refKey, refValue) -> val data = this[refKey]; data == null || data == refValue}

/**
 * Modifies the receiver list **in place** using the modifier `block`. Returns `this` list for consistency
 */
inline fun <T> MutableList<T>.modify(block: (T) -> T): MutableList<T> {
    for (i in indices) {
        set(i, block(this[i]))
    }
    return this
}

inline fun <T> Collection<T>.addFront(vararg element: T): List<T> {
    val result = ArrayList<T>(size + element.size)
    result.addAll(element)
    result.addAll(this)
    return result
}

/**
 * Sorts `this` list in place according to their `weights` in the provided list. Stable sorting, meaning that
 *  two identical `weights` will preserve their original order
 */
inline fun <T> MutableList<T>.weightedSort(weights: List<Int>) {
    val associations = this.associateIndexed { i, t -> t to weights[i] }
    this.sortBy { associations[it] }
}

inline infix fun IntRange.shifted(shift: Int): IntRange = (first + shift) .. (last + shift)
