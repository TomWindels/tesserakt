package dev.tesserakt.sparql.runtime.stream

import dev.tesserakt.sparql.util.Cardinality
import kotlin.jvm.JvmInline

@JvmInline
internal value class CollectedStream<E: Any>(private val data: List<E>): Stream<E>, OptimisedStream<E>, List<E> by data {

    override val cardinality: Cardinality get() = Cardinality(data.size)

    override val description: String
        get() = "Collected(size = ${data.size})"

    companion object {

        operator fun <E: Any> invoke(stream: Stream<E>) = CollectedStream<E>(
            data = buildList(stream.cardinality.coerceAtMost(Cardinality(1000)).toInt()) {
                val iter = stream.iterator()
                while (iter.hasNext()) {
                    add(iter.next())
                }
            }
        )

    }

}
