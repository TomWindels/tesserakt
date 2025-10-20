package dev.tesserakt.sparql.runtime.query.select

import dev.tesserakt.sparql.runtime.evaluation.mapping.Mapping
import dev.tesserakt.sparql.util.SortedCounter
import kotlin.jvm.JvmInline

@JvmInline
value class UnconfinedOrderedOutputState private constructor(
    private val results: SortedCounter<Mapping>
) : OutputState.Unconfined {

    constructor(comparator: Comparator<Mapping>): this(results = SortedCounter<Mapping>(comparator = comparator))

    override val size: Int
        get() = results.flattened.size

    override fun onResultAdded(result: Mapping) = results.increment(result)

    override fun onResultRemoved(result: Mapping) = results.decrement(result)

    override fun isEmpty() = results.flattened.isEmpty()

    override fun iterator(): Iterator<Mapping> = results.flattened.iterator()

    override fun contains(element: Mapping) = element in results

    override fun containsAll(elements: Collection<Mapping>) = throw UnsupportedOperationException()

}
