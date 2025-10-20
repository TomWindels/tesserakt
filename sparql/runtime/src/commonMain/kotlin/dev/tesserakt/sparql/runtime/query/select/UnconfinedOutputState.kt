package dev.tesserakt.sparql.runtime.query.select

import dev.tesserakt.sparql.runtime.evaluation.mapping.Mapping
import dev.tesserakt.sparql.util.Counter
import kotlin.jvm.JvmInline

@JvmInline
value class UnconfinedOutputState private constructor(
    private val results: Counter<Mapping>
): OutputState.Unconfined {

    constructor(): this(Counter())

    override val size: Int
        get() = results.flattened.size

    override fun onResultAdded(result: Mapping) = results.increment(result)

    override fun onResultRemoved(result: Mapping) = results.decrement(result)

    override fun isEmpty() = results.flattened.isEmpty()

    override fun iterator(): Iterator<Mapping> = results.flattened.iterator()

    override fun contains(element: Mapping) = element in results

    override fun containsAll(elements: Collection<Mapping>) = throw UnsupportedOperationException()

}
