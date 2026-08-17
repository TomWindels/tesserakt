package dev.tesserakt.sparql.runtime.query

import dev.tesserakt.sparql.QueryStatistics
import dev.tesserakt.sparql.runtime.collection.MappingArrayHint
import dev.tesserakt.sparql.runtime.collection.ReindexableMappingArray
import dev.tesserakt.sparql.runtime.evaluation.*
import dev.tesserakt.sparql.runtime.evaluation.context.QueryContext
import dev.tesserakt.sparql.runtime.evaluation.mapping.Mapping
import dev.tesserakt.sparql.runtime.evaluation.mapping.hashable
import dev.tesserakt.sparql.runtime.stream.*
import dev.tesserakt.sparql.util.Cardinality

class InclusionFilterState(
    private val inner: MutableJoinState,
    private val filter: MutableJoinState,
): MutableJoinState {

    private val buf = ReindexableMappingArray()

    override val properties: MutableJoinState.Properties
        get() = inner.properties

    override val cardinality: Cardinality
        get() = buf.cardinality

    init {
        inner.reindex(
            bindings = inner.properties.guaranteed.intersect(filter.properties.maximum),
            hint = MappingArrayHint(
                partialHashAccess = false
            )
        )
        filter.reindex(
            bindings = filter.properties.guaranteed.intersect(inner.properties.guaranteed),
            hint = MappingArrayHint(
                partialHashAccess = false
            )
        )

        inner
            .join(MappingAddition(Mapping.EMPTY, null))
            .filtered { filter.join(it).iterator().hasNext() }
            .forEach { delta ->
                check(delta is MappingAddition)
                buf.add(delta.value)
            }
    }

    override fun join(delta: MappingDelta): Stream<MappingDelta> {
        return delta.mapToStream { buf.join(it) }
    }

    override fun reindex(
        bindings: BindingIdentifierSet,
        hint: MappingArrayHint
    ) {
        buf.reindex(bindings, hint)
    }

    override fun process(delta: DataDelta): OptimisedStream<MappingDelta> {
        val filterChanges = filter
            .process(delta)
            .groupingBy { it.value.retain(inner.properties.guaranteed).hashable() }
            .fold(0) { acc, delta ->
                val d = when (delta) {
                    is MappingAddition -> 1
                    is MappingDeletion -> -1
                }
                acc + d
            }
        if (filterChanges.isEmpty()) {
            val changes = inner
                .process(delta)
                // eager as we would collect it immediately otherwise anyway
                .filter { delta -> filter.join(delta).iterator().hasNext() }
            changes.forEach { delta ->
                when (delta) {
                    is MappingAddition -> buf.add(delta.value)
                    is MappingDeletion -> buf.remove(delta.value)
                }
            }
            return changes.toStream()
        } else {
            var result: Stream<MappingDelta> = emptyStream()
            filterChanges.forEach { (mapping, count) ->
                when {
                    count < 0 -> {
                        // we are potentially blocking more results - we can check which ones by using our
                        //  prior state
                        val removed = buf
                            .iter(mapping.inner)


//                                .filtered { it.compatibleWith(mapping.inner) }


                            .mapped { MappingDeletion(it, null) }
                            // we don't want to mark items as removed if they are still allowed by the filter
                            //  (the changes applied to the filter were not enough to fully block these results)
                            .filtered { !filter.join(it).iterator().hasNext() }
                            // we alter our buffer immediately as well, so we get the results eagerly
                            .collect()
                        if (removed.isNotEmpty()) {
                            result = result.chain(removed)
                            removed.forEach { buf.remove(it.value) }
                        }
                    }
                    count > 0 -> {
                        // we are potentially blocking less results - we can let the *outdated* inner state
                        //  join with this newly made available solution, and check if our *updated* state still
                        //  lets it through
                        val new = inner
                            .join(MappingAddition(mapping.inner, null))
                            // we don't want to send the same exact value out again if we weren't blocking it
                            //  in the first place
                            .filtered { !buf.iter(it.value).join(it.value).iterator().hasNext() }
                            // but we do want to send it out if it is no longer being blocked
                            .filtered { filter.join(it).iterator().hasNext() }
                            // we don't want to add any of our own bindings to the result set
                            .mapped { delta -> delta.map { mapping -> mapping.retain(inner.properties.maximum) } }
                            .collect()
                        if (new.isNotEmpty()) {
                            result = result.chain(new)
                            new.forEach { buf.add(it.value) }
                        }
                    }
                }
            }
            // we also apply the newest changes observed in our inner state with this updated filter block
            val changes = inner
                .process(delta)
                // eager as we would collect it immediately otherwise anyway
                .filter { delta -> filter.join(delta).iterator().hasNext() }
            changes.forEach { delta ->
                when (delta) {
                    is MappingAddition -> buf.add(delta.value)
                    is MappingDeletion -> buf.remove(delta.value)
                }
            }
            result = result.chain(changes.toStream())
            return result.optimisedForSingleUse()
        }
    }

    override fun stats(
        context: QueryContext,
        granularity: QueryStatistics.Granularity
    ): Statistics {
        return Statistics.SelectiveElement(
            cardinality = cardinality,
            inner = Statistics.JoinedElement(
                left = inner.stats(context, granularity),
                right = Statistics.DescriptionElement(
                    inner = filter.stats(context, granularity),
                    description = "FILTER EXISTS"
                )
            )
        )
    }
}
