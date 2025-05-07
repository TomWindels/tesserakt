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

/**
 * Replaces the value associated with [key] with the value computed by [transform]ing the original value (if any)
 */
expect inline fun <K, V: Any> MutableMap<K, V>.replace(key: K, crossinline transform: (V?) -> V?)

/**
 * Drops at most one occurrence of every element inside [elements]. Order of the returned list is not guaranteed! If
 *  none of the [elements] are present inside this list, the original instance is returned.
 */
inline fun <T> List<T>.unorderedDrop(elements: Iterable<T>): List<T> {
    val iter = elements.iterator()
    // finding the first one that is actually present
    while (iter.hasNext()) {
        val i = indexOf(iter.next())
        // ensuring there's an element to remove first, allowing us to delay the copy creation as long as possible
        if (i == -1) {
            continue
        }
        // creating a copy, and removing this element
        val copy = toMutableList()
        copy.unorderedDropAt(i)
        /// continuing with the remainder of the set
        while (iter.hasNext()) {
            copy.unorderedDropAt(indexOf(iter.next()))
        }
        return copy
    }
    return this
}

/**
 * Removes element at [index], without! preserving element order for quick removal
 */
inline fun <T> MutableList<T>.unorderedDropAt(index: Int) {
    when {
        // also covers size == 0
        index == -1 -> {
            return
        }
        // size = 1, element to be removed = 0 (as it isn't -1), so only clearing the list
        size == 1 -> {
            clear()
        }
        index == size - 1 -> {
            removeLastElement()
        }
        else -> {
            this[index] = removeLastElement()
        }
    }
}

inline fun <K, V: Any> Iterable<K>.associateWithNotNull(transform: (K) -> V?): Map<K, V> = buildMap {
    this@associateWithNotNull.forEach { key ->
        val value = transform(key) ?: return@forEach
        put(key, value)
    }
}

/**
 * A custom implementation of the [MutableList.removeFirst] method, implemented to make sure it resolves properly on
 *  Android 14 and below
 */
expect inline fun <T> MutableList<T>.removeFirstElement(): T

/**
 * A custom implementation of the [MutableList.removeLast] method, implemented to make sure it resolves properly on
 *  Android 14 and below
 */
expect inline fun <T> MutableList<T>.removeLastElement(): T
