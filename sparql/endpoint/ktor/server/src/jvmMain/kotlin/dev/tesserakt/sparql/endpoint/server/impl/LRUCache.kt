package dev.tesserakt.sparql.endpoint.server.impl

// src: https://medium.com/@fasilt/understanding-lru-least-recently-used-cache-in-kotlin-b54c7060e752
internal class LRUCache<K, V>(
    private val capacity: Int,
    private val onEviction: (K, V) -> Unit = { _, _ -> },
) : LinkedHashMap<K, V>(capacity, 0.75f, true) {

    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean {
        if (size > capacity) {
            onEviction(eldest!!.key, eldest.value)
            return true
        }
        return false
    }
}
