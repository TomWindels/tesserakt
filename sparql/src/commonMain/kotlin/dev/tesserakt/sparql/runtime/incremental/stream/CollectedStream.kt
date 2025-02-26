package dev.tesserakt.sparql.runtime.incremental.stream

import kotlin.jvm.JvmInline

@JvmInline
internal value class CollectedStream<E: Any>(private val data: List<E>): Stream<E>, List<E> by data {

    override val cardinality: Int get() = data.size

    companion object {

        operator fun <E: Any> invoke(stream: Stream<E>) = CollectedStream<E>(
            data = buildList(stream.cardinality.coerceAtMost(1000)) {
                val iter = stream.iterator()
                while (iter.hasNext()) {
                    add(iter.next())
                }
            }
        )

    }

}
