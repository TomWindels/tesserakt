package dev.tesserakt.sparql.runtime.incremental.stream


internal class CachedStream<E: Any>(private val input: Stream<E>): Stream<E> {

    private var _cache: CollectedStream<E>? = null

    private val cached: CollectedStream<E>
        get() = _cache ?: load()

    override val cardinality: Int
        get() = cached.cardinality

    private fun load(): CollectedStream<E> {
        val collected = input.collect()
        _cache = collected
        return collected
    }

    override fun isEmpty() = cached.isEmpty()

    override fun iterator(): Iterator<E> {
        return cached.iterator()
    }

}
