package dev.tesserakt.sparql.runtime.stream

import dev.tesserakt.sparql.util.Cardinality
import kotlin.jvm.JvmInline

/**
 * A stream variant similar to [CollectedStream], but with a reusable buffer: the stream buffer can be reset for
 *  subsequent `collectTo()` calls
 */
@JvmInline
value class CachedStream<E: Any> private constructor(private val buffer: ArrayList<E>): Stream<E>, OptimisedStream<E> {

    constructor(): this(ArrayList())

    override val cardinality: Cardinality get() = Cardinality(buffer.size)

    override val description: String
        get() = "Cached(size = ${buffer.size})"

    override fun supportsReuse(): Boolean {
        return true
    }

    override fun iterator(): Iterator<E> {
        return buffer.iterator()
    }

    fun clear() {
        buffer.clear()
    }

    fun insert(stream: Stream<E>) {
        stream.forEach { item ->
            buffer.add(item)
        }
    }

}
