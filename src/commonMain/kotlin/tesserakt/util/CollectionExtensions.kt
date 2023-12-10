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

//inline fun <K, V> Iterable<Map<K, V>>.merge() =
//    buildMap { this@merge.forEach { putAll(it) } }
//
//inline fun <K, V> Iterable<Map<K, V>>.mergeIfMatching(): Map<K, V>? = buildMap {
//    this@mergeIfMatching.forEach { map ->
//        // checking if it is inside it first, so `null == null` doesn't happen
//        if (map.any { (k, v) -> k in this && this[k] != v }) {
//            return null
//        }
//        putAll(map)
//    }
//}
//
//inline fun <K, V: Any> Iterable<Map<K, V>>.mergeIfMatching(): Map<K, V>? = buildMap {
//    this@mergeIfMatching.forEach { map ->
//        // V is non-null, so contains check not required
//        if (map.any { (k, v) -> this[k] != v }) {
//            return null
//        }
//        putAll(map)
//    }
//}
