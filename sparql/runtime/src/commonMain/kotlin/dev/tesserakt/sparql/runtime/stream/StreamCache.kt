package dev.tesserakt.sparql.runtime.stream

class StreamCache<K : Any, E : Any> {

    private var key: K? = null
    private val cache = CachedStream<E>()

    fun get(key: K): OptimisedStream<E>? {
        return if (this.key == key) cache else null
    }

    fun set(key: K, stream: Stream<E>): OptimisedStream<E> {
        this.key = key
        cache.clear()
        stream.collectTo(cache)
        return cache
    }

    inline fun getOrCache(key: K, block: () -> Stream<E>): OptimisedStream<E> {
        return get(key) ?: set(key, block())
    }

    fun clear() {
        key = null
        cache.clear()
    }

}
