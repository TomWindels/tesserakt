package dev.tesserakt.sparql.runtime.query

import dev.tesserakt.sparql.QueryStatistics
import dev.tesserakt.sparql.runtime.collection.MappingArray
import dev.tesserakt.sparql.runtime.collection.MappingArrayHint
import dev.tesserakt.sparql.runtime.evaluation.*
import dev.tesserakt.sparql.runtime.evaluation.context.QueryContext
import dev.tesserakt.sparql.runtime.evaluation.mapping.Mapping
import dev.tesserakt.sparql.runtime.evaluation.mapping.hashable
import dev.tesserakt.sparql.runtime.stream.*
import dev.tesserakt.sparql.util.Cardinality
import dev.tesserakt.sparql.util.Counter
import dev.tesserakt.sparql.util.OneCardinality

class OptionalState(
    private val inner: MutableJoinState,
    private val optional: MutableJoinState,
    filters: List<FilterExpression>,
): MutableJoinState {

    private val filters = filters.filter { filter ->
        val innerOverlap = filter.bindings.intersectSize(inner.properties.maximum)
        val optionalOverlap = filter.bindings.intersectSize(optional.properties.maximum)
        // we only want to apply this filter where it makes sense the most;
        // in order to satisfy this requirement,
        // neither side has *none* of the required bindings
        innerOverlap != 0 && optionalOverlap != 0 &&
        // nor do either of them have *all* of the bindings, as the filter would be applied there
        //  instead
        innerOverlap != filter.bindings.size && optionalOverlap != filter.bindings.size &&
        // but combined they do
        filter.bindings in (inner.properties.maximum + optional.properties.maximum)
    }

    override val properties = MutableJoinState.Properties(
        guaranteed = inner.properties.guaranteed,
        maximum = inner.properties.maximum + optional.properties.maximum,
    )

    private var indexedBindings = BindingIdentifierSet.EMPTY
    private var arrayHint = MappingArrayHint()
    private var state = createState()

    override val cardinality: Cardinality
        get() = state.cardinality

    override fun join(delta: MappingDelta): Stream<MappingDelta> {
        return delta.mapToStream { mapping -> state.join(mapping) }
    }

    override fun reindex(
        bindings: BindingIdentifierSet,
        hint: MappingArrayHint
    ) {
        if (this.indexedBindings == bindings && this.arrayHint == hint) {
            return
        }
        this.indexedBindings = bindings
        this.arrayHint = hint
        val newArray = MappingArray(indexedBindings, arrayHint)
        state.iter().forEach {
            newArray.add(it)
        }
        state = newArray
    }

    override fun process(delta: DataDelta): OptimisedStream<MappingDelta> {
        if (!optional.process(delta).iterator().hasNext()) {
            val total = inner
                .process(delta)
                .transform(OneCardinality) { delta -> optional.join(delta).orElse(delta) }
                .filtered { delta -> filters.all { it.test(delta.value) } }
                .collect()
            total.forEach { delta ->
                when (delta) {
                    is MappingAddition -> state.add(delta.value)
                    is MappingDeletion -> state.remove(delta.value)
                }
            }
            return total
        }
        // we update the inner state, but discard what has actually changed as the relationship between these two are
        //  complex; we completely re-evaluate it instead
        inner.process(delta)
        // we have to recalculate the entire state from nothing, combined with the impact of the peeked change,
        //  and have that ripple through all our optionals
        val newState = createState()
        // our peeked delta is the difference between this new state and our existing state
        val c1 = Counter(state.iter().mapped { it.hashable() })
        val c2 = Counter(newState.iter().mapped { it.hashable() })
        val total = c1.current + c2.current
        val diffs = total.associateWith { c2[it] - c1[it] }
        val changes = CollectedStream(
            data = diffs.asIterable().flatMap { (mapping, count) ->
                when {
                    count == 0 -> emptyList()
                    count > 0 -> {
                        List(count) { MappingAddition(mapping.inner, delta) }
                    }

                    else -> {
                        List(-count) { MappingDeletion(mapping.inner, delta) }
                    }
                }
            }
        )
        state = newState
        return changes
    }

    override fun stats(
        context: QueryContext,
        granularity: QueryStatistics.Granularity
    ): Statistics {
        val inner = Statistics.JoinedElement(
            left = inner.stats(context, granularity),
            right = Statistics.DescriptionElement(
                description = "OPTIONAL",
                inner = optional.stats(context, granularity)
            )
        )
        if (granularity isAtLeast QueryStatistics.Granularity.DETAILED && filters.isNotEmpty()) {
            return Statistics.DescriptionElement(
                description = "Filtered\n${filters.joinToString("\n")}",
                inner = inner,
            )
        }
        return Statistics.SelectiveElement(
            cardinality = cardinality,
            inner = inner,
        )
    }

    private fun createState(): MappingArray {
        val final = inner
            .join(MappingAddition(Mapping.EMPTY, null))
            .transform(OneCardinality) { delta -> optional.join(delta).orElse(delta) }
        // these should all be mapping additions
        val result = MappingArray(indexedBindings, arrayHint)
        final.forEach { delta ->
            check(delta is MappingAddition)
            if (filters.all { it.test(delta.value) }) {
                result.add(delta.value)
            }
        }
        return result
    }

}

/**
 * Simplifies a stream of deltas, removing opposing changes, potentially reducing the total number of elements.
 */
private fun Stream<MappingDelta>.simplified(): CollectedStream<MappingDelta> {
    val combined = this
        // we need to make it hashable for `groupingBy` to work correctly
        .groupingBy { it.value.hashable() }
        .fold({ _, _ -> 0 }) { _, count, delta ->
            val d = if (delta is MappingAddition) 1 else -1
            count + d
        }
    return CollectedStream(
        data = combined.asIterable().flatMap { (mapping, count) ->
            when {
                count == 0 -> emptyList()
                count > 0 -> {
                    List(count) { MappingAddition(mapping.inner, null) }
                }
                else -> {
                    List(-count) { MappingDeletion(mapping.inner, null) }
                }
            }
        }
    )
}
