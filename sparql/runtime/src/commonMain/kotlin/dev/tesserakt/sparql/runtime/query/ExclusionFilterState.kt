package dev.tesserakt.sparql.runtime.query

import dev.tesserakt.sparql.QueryStatistics
import dev.tesserakt.sparql.runtime.collection.MappingArrayHint
import dev.tesserakt.sparql.runtime.collection.ReindexableMappingArray
import dev.tesserakt.sparql.runtime.evaluation.*
import dev.tesserakt.sparql.runtime.evaluation.context.QueryContext
import dev.tesserakt.sparql.runtime.evaluation.mapping.HashableMapping
import dev.tesserakt.sparql.runtime.evaluation.mapping.Mapping
import dev.tesserakt.sparql.runtime.evaluation.mapping.hashable
import dev.tesserakt.sparql.runtime.stream.*
import dev.tesserakt.sparql.util.Cardinality
import dev.tesserakt.sparql.util.Counter
import dev.tesserakt.sparql.util.ZeroCardinality


object ExclusionFilterState {

    operator fun invoke(
        inner: MutableJoinState,
        filter: MutableJoinState,
    ): MutableJoinState {
        // we have three possible implementors to choose from:
        // * if we see that `filter` references no bindings found in `inner`, we can use the `broad` variant
        // * if we see that `filter` only references bindings found in `inner` that are guaranteed to be present, we can
        //  use the `narrow` variant
        // * otherwise, we have to fall back to `full` for correct results
        val guaranteedCommon = filter.properties.maximum.intersectSize(inner.properties.guaranteed)
        val maximumCommon = filter.properties.maximum.intersectSize(inner.properties.maximum)
        return when {
            maximumCommon == 0 -> {
                // guaranteedCommon is also 0
                Broad(inner = inner, filter = filter)
            }
            guaranteedCommon == maximumCommon -> {
                // they have to be the same binding names
                Narrow(inner = inner, filter = filter)
            }
            else -> {
                // there's at least one binding name that is not always present
                Full(inner = inner, filter = filter)
            }
        }
    }

    /**
     * The typical exclude filter, where its internal state affects parts of the results from its parent; those
     *  affected have shared binding names, represented using the [commonBindingNames]
     *  collection (which may not be empty!)
     */
    class Narrow(
        private val inner: MutableJoinState,
        private val filter: MutableJoinState,
    ) : MutableJoinState {

        private val commonBindingNames = inner.properties.maximum.intersect(filter.properties.maximum)
        // tracking what binding groups are "invalid" (= should be filtered out)
        private val filtered = Counter<HashableMapping>()

        override val properties: MutableJoinState.Properties
            // we only affect cardinality, we do not alter the shape of the output
            get() = inner.properties

        override val cardinality: Cardinality
            // is correct in the worst case - we don't track the complete state so this is the best we can do
            get() = inner.cardinality


        init {
            require(commonBindingNames.isNotEmpty()) { "Invalid filter use detected!" }
            require(commonBindingNames.intersectSize(inner.properties.guaranteed) == commonBindingNames.size) { "Invalid filter use detected!" }

            // we only need exact matches with our common binding names
            inner.reindex(commonBindingNames, MappingArrayHint.DEFAULT)

            filter
                .join(MappingAddition(Mapping.EMPTY))
                .forEach { mappingDelta ->
                    when (mappingDelta) {
                        is MappingAddition -> filtered.increment(mappingDelta.value.retain(commonBindingNames).hashable())
                        // highly unlikely occurrence considering we're joining on an empty mapping
                        is MappingDeletion -> filtered.decrement(mappingDelta.value.retain(commonBindingNames).hashable())
                    }
                }
        }

        override fun reindex(bindings: BindingIdentifierSet, hint: MappingArrayHint) {
            // no-op, we need our index
        }

        override fun enqueue(delta: DataDelta) {
            inner.enqueue(delta)
            filter.enqueue(delta)
        }

        override fun process(): OptimisedStream<MappingDelta> {
            val filterChanges = filter
                .process()
                .groupingBy { it.value.retain(commonBindingNames).hashable() }
                .fold(0) { acc, delta ->
                    val change = when (delta) {
                        is MappingAddition -> 1
                        is MappingDeletion -> -1
                    }
                    acc + change
                }
            // these changes, combined with the existing `filtered` state, will result in a set of bindings that can
            //  now be joined with to find all resulting changes:
            // * change additions (not in filtered now, but in `changes`) => these have to be removed outwards
            // * change deletions (in filtered now, but removed in `changes`) => these have to be added outwards
            var result: Stream<MappingDelta> = emptyStream()

            // before we update the `inner` state, we want to delete all values that no longer satisfy our updated
            //  filter; because `inner` is outdated, the deletions it yields match what we outputted previously
            filterChanges.forEach { (mapping, count) ->
                val current = filtered[mapping]
                if (count > 0 && current == 0) {
                    // it's getting filtered out, meaning it's deletion becomes the result of the change
                    // we have to collect it here as we're about to update `inner`s state
                    val deleted = inner
                        .join(MappingDeletion(mapping.inner))
                        .collect()
                    result = result.chain(deleted)
                }
            }

            // we can now update the `inner` state, and propagate its own set of changes selectively: if they match a
            //  completely unfiltered mapping (both before and after our `filter` change), they are let through as-is
            val innerChanges = inner
                .process()
                .filtered { delta ->
                    val hashed = delta.value.retain(commonBindingNames).hashable()

                    // could be filtered out before
                    if (filtered[hashed] != 0) {
                        return@filtered false
                    }

                    // could be filtered out now
                    filterChanges[hashed].let { it == null || it == 0 }
                }
                // has to be evaluated eagerly to prevent `filterChanges` having been applied
                //  before this filtering logic is done
                .collect()

            // after we update the inner state, we want to add all values that now do satisfy our updated filter;
            //  these changes were not let through during the `inner.process()` logic, as it's possible the inner change
            //  set would be complex
            filterChanges.forEach { (mapping, count) ->
                val current = filtered[mapping]
                if (count < 0 && current <= -count) {
                    // it's no longer being filtered out, meaning it's addition becomes the result of the change
                    result = result.chain(inner.join(MappingAddition(mapping.inner)))
                }
            }

            // we now combine the inner changes with our final result set
            result = result.chain(innerChanges)

            // we now also have to update our filtered state
            filterChanges.forEach { (mapping, change) ->
                // works for negative changes too
                filtered.increment(mapping, change)
            }

            return result.optimisedForSingleUse()
        }

        override fun stats(context: QueryContext, granularity: QueryStatistics.Granularity): Statistics {
            return Statistics.JoinedElement(
                left = inner.stats(context, granularity),
                right = Statistics.DescriptionElement(
                    inner = filter.stats(context, granularity),
                    description = "FILTER NOT EXISTS\nnarrow"
                )
            )
        }

        override fun join(delta: MappingDelta): Stream<MappingDelta> {
            if (commonBindingNames in delta.value.bindings) {
                val hashed = delta.value.retain(commonBindingNames).hashable()
                // if we're trying to join on a mapping that we can already see is being blocked, we can terminate early
                if (filtered[hashed] > 0) {
                    return emptyStream()
                }
                // we can join unchecked - the mapping matches the type of mappings we check against, but doesn't
                //  satisfy the values we block
                return inner.join(delta)
            }
            // we have to join with the unfiltered state, and filter everything that comes out, instead
            return inner
                .join(delta)
                .filtered { mapping -> filtered[mapping.value.retain(commonBindingNames).hashable()] <= 0 }
        }

    }

    /**
     * Special variant of the exclude filter, where the # of common bindings is zero, meaning that a satisfied internal
     *  state means no bindings are coming through
     */
    class Broad(
        private val inner: MutableJoinState,
        private val filter: MutableJoinState,
    ) : MutableJoinState {

        private var count = filter
            .join(MappingAddition(Mapping.EMPTY))
            .fold(0) { acc, mappingDelta ->
                when (mappingDelta) {
                    is MappingAddition -> acc + 1
                    is MappingDeletion -> acc - 1
                }
            }

        override val properties: MutableJoinState.Properties
            get() = inner.properties

        override val cardinality: Cardinality
            get() = if (count > 0) ZeroCardinality else inner.cardinality

        override fun enqueue(delta: DataDelta) {
            inner.enqueue(delta)
            filter.enqueue(delta)
        }

        override fun process(): OptimisedStream<MappingDelta> {
            val change = filter.process().fold(0) { acc, mappingDelta ->
                when (mappingDelta) {
                    is MappingAddition -> acc + 1
                    is MappingDeletion -> acc - 1
                }
            }
            var result: Stream<MappingDelta> = emptyStream()
            // if the count becomes > 0 through this delta, all mappings should be removed, and we ignore the results
            //  generated by the inner state
            when {
                count == 0 && change != 0 -> {
                    check(change > 0) { "Invalid internal state!" }
                    result = result
                        .chain(inner.join(MappingDeletion(Mapping.EMPTY)))
                        .chain(inner.process().filtered { it is MappingDeletion })
                }
                count == 0 /* && change == 0 */ -> {
                    // our filter hasn't changed (still allows everything to go through), we only apply the changes
                    //  observed by the inner state
                    result = result.chain(inner.process())
                }
                count + change == 0 /* && count != 0 */ -> {
                    // considering we were blocking everything before this point, we are only allowed to emit new
                    //  changes that *add* new results
                    result = result
                        .chain(inner.join(MappingAddition(Mapping.EMPTY)))
                        .chain(inner.process().filtered { it is MappingAddition })
                }
                else /* count != 0 && count + change != 0 */ -> {
                    // we were already blocking, and keep blocking, the inner state;
                    //  we let the inner state process its changes, ignoring its result
                    inner.process()
                }
            }
            // we've now fully applied the set of changes, so we can
            count += change
            return result.optimisedForSingleUse()
        }

        override fun join(delta: MappingDelta): Stream<MappingDelta> {
            if (count > 0) {
                return emptyStream()
            }
            return inner.join(delta)
        }

        override fun reindex(bindings: BindingIdentifierSet, hint: MappingArrayHint) {
            inner.reindex(bindings, hint)
        }

        override fun stats(context: QueryContext, granularity: QueryStatistics.Granularity): Statistics {
            return Statistics.JoinedElement(
                left = inner.stats(context, granularity),
                right = Statistics.DescriptionElement(
                    inner = filter.stats(context, granularity),
                    description = "FILTER NOT EXISTS\nbroad"
                )
            )
        }

    }

    /**
     * A very thorough version of the filter logic. Checks every solution independently, as the overlap between the
     *  joined state and the filter block has some overlap, with that overlap being *optionally* bound.
     */
    class Full(
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
                .join(MappingAddition(Mapping.EMPTY))
                .filtered { !filter.join(it).iterator().hasNext() }
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

        override fun enqueue(delta: DataDelta) {
            inner.enqueue(delta)
            filter.enqueue(delta)
        }

        override fun process(): OptimisedStream<MappingDelta> {
            val filterChanges = filter
                .process()
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
                    .process()
                    // eager as we would collect it immediately otherwise anyway
                    .filter { delta -> !filter.join(delta).iterator().hasNext() }
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
                        count > 0 -> {
                            // we are potentially blocking more results - we can check which ones by using our
                            //  prior state
                            val removed = buf
                                .iter(mapping.inner)
                                .mapped { MappingDeletion(it) }
                                // we need to make sure the up-to-date filter state can join with it before
                                //  we can actually mark it as blocked
                                .filtered { filter.join(it).iterator().hasNext() }
                                // we alter our buffer immediately as well, so we get the results eagerly
                                .collect()
                            if (removed.isNotEmpty()) {
                                result = result.chain(removed)
                                removed.forEach { buf.remove(it.value) }
                            }
                        }

                        count < 0 -> {
                            // we are potentially blocking less results - we can let the *outdated* inner state
                            //  join with this newly made available solution, and check if our *updated* state still
                            //  lets it through
                            val new = inner
                                .join(MappingAddition(mapping.inner))
                                // we don't want to send the same exact value out again if we weren't blocking it
                                //  in the first place
                                .filtered { !buf.iter(it.value).join(it.value).iterator().hasNext() }
                                // but we do want to send it out if it is no longer being blocked
                                .filtered { !filter.join(it).iterator().hasNext() }
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
                    .process()
                    // eager as we would collect it immediately otherwise anyway
                    .filter { delta -> !filter.join(delta).iterator().hasNext() }
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
                        description = "FILTER NOT EXISTS\nfull"
                    )
                )
            )
        }
    }

}
