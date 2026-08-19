package dev.tesserakt.sparql.runtime.query.select

import dev.tesserakt.sparql.runtime.evaluation.mapping.HashableMapping
import dev.tesserakt.sparql.runtime.evaluation.mapping.Mapping
import dev.tesserakt.sparql.runtime.evaluation.mapping.hashable
import dev.tesserakt.sparql.runtime.stream.mapped
import dev.tesserakt.sparql.runtime.stream.toStream
import dev.tesserakt.sparql.util.SortedCounter
import kotlin.jvm.JvmInline

@JvmInline
value class UnconfinedOrderedOutputState private constructor(
    private val results: SortedCounter<HashableMapping>
) : OutputState.Unconfined {

    constructor(comparator: Comparator<Mapping>): this(
        results = SortedCounter(Comparator { a, b -> comparator.compare(a.inner, b.inner) })
    )

    override val size: Int
        get() = results.flattened.size

    override fun onResultAdded(result: Mapping) = results.increment(result.hashable())

    override fun onResultRemoved(result: Mapping) = results.decrement(result.hashable())

    override fun isEmpty() = results.flattened.isEmpty()

    override fun iterator(): Iterator<Mapping> = results
        .flattened
        .toStream()
        .mapped { it.inner }
        .iterator()

    override fun contains(element: Mapping) = element.hashable() in results

    override fun containsAll(elements: Collection<Mapping>) = throw UnsupportedOperationException()

}
