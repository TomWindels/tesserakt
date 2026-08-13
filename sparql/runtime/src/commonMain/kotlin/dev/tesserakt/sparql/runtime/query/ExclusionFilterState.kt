package dev.tesserakt.sparql.runtime.query

import dev.tesserakt.sparql.QueryStatistics
import dev.tesserakt.sparql.runtime.collection.MappingArray
import dev.tesserakt.sparql.runtime.collection.MappingArrayHint
import dev.tesserakt.sparql.runtime.evaluation.*
import dev.tesserakt.sparql.runtime.evaluation.context.QueryContext
import dev.tesserakt.sparql.runtime.evaluation.mapping.HashableMapping
import dev.tesserakt.sparql.runtime.evaluation.mapping.Mapping
import dev.tesserakt.sparql.runtime.evaluation.mapping.hashable
import dev.tesserakt.sparql.runtime.stream.*
import dev.tesserakt.sparql.types.Filter
import dev.tesserakt.sparql.util.Cardinality
import dev.tesserakt.sparql.util.Counter
import dev.tesserakt.util.replace

sealed interface ExclusionFilterState: MutableFilterState {

    /**
     * Peeks the total impact this filter has when applying the [delta] in this state
     */
    override fun peek(delta: DataDelta): OptimisedStream<MappingDelta>

    /**
     * Filters the [input] stream, using its processed internal state after applying the [delta]
     */
    override fun filter(input: Stream<MappingDelta>, delta: DataDelta): Stream<MappingDelta>

    /**
     * Filters the [input] stream, using only its processed internal state
     */
    override fun filter(input: Stream<MappingDelta>): Stream<MappingDelta>

    override fun process(delta: DataDelta)

    override fun stats(context: QueryContext, granularity: QueryStatistics.Granularity): Statistics

    /**
     * The typical exclude filter, where its internal state affects parts of the results from its parent; those
     *  affected have shared binding names, represented using the [commonBindingNames]
     *  collection (which may not be empty!)
     */
    class Narrow(
        private val commonBindingNames: BindingIdentifierSet,
        private val state: MutableJoinState,
    ) : ExclusionFilterState {

        // tracking what binding groups are "invalid" (= should be filtered out)
        private val filtered = Counter<HashableMapping>()

        init {
            require(commonBindingNames.isNotEmpty()) { "Invalid filter use detected!" }
            state
                .join(MappingAddition(Mapping.EMPTY, null))
                .forEach { mappingDelta ->
                    when (mappingDelta) {
                        is MappingAddition -> filtered.increment(mappingDelta.value.retain(commonBindingNames).hashable())
                        // highly unlikely occurrence considering we're joining on an empty mapping
                        is MappingDeletion -> filtered.decrement(mappingDelta.value.retain(commonBindingNames).hashable())
                    }
                }
        }

        override fun peek(delta: DataDelta): OptimisedStream<MappingDelta> {
            peekStateChange(delta)
            // these changes, combined with the `filtered` state, will result in a set of bindings that can now be joined
            //  with to find all resulting changes:
            // * change additions (not in filtered now, but in `changes`) => these have to be removed outwards
            // * change deletions (in filtered now, but removed in `changes`) => these have to be added outwards
            val results = mutableListOf<MappingDelta>()
            lastChanges.forEach { (mapping, count) ->
                val current = filtered[mapping]
                when {
                    count < 0 && current <= -count -> {
                        // it's no longer being filtered out, meaning it's addition becomes the result of the peek
                        results.add(MappingAddition(value = mapping.inner, origin = null))
                    }

                    count > 0 && current == 0 -> {
                        // it's now being filtered out, meaning it's removal becomes the result of the peek
                        results.add(MappingDeletion(value = mapping.inner, origin = null))
                    }
                }
            }
            return CollectedStream(results)
        }

        /**
         * Filters the [input] stream, using its processed internal state after applying the [delta]
         */
        override fun filter(input: Stream<MappingDelta>, delta: DataDelta): Stream<MappingDelta> {
            // we track the impact of the delta here directly
            peekStateChange(delta)
            // using that to filter the incoming result
            return when {
                lastChanges.isEmpty() && filtered.count == 0 -> {
                    input
                }
                filtered.count == 0 -> {
                    input.filtered { mapping ->
                        val retained = mapping.value.retain(commonBindingNames)
                        if (retained.count == commonBindingNames.size) {
                            // the changes should not be negative as we're not filtering anything
                            (lastChanges[retained.hashable()] ?: 0) <= 0
                        } else {
                            // we have to check if any of our peeked changes can join with the mapping
                            // using the map is still an O(N) lookup, but it is reduced as we don't check
                            //  duplicates
                            lastChanges.none { (blocked, _) -> blocked.inner.compatibleWith(mapping.value) }
                        }
                    }
                }
                lastChanges.isEmpty() -> {
                    // the `delta` has no impact
                    filter(input)
                }
                else -> {
                    // we have to construct a temporary combined state to reason about the final verdict
                    //  of a given mapping
                    // we do it lazily, as this can be possibly expensive, and is only required if we have to detect
                    //  changes that do not have all of our common mappings present (`OPTIONAL` blocks), which is
                    //  rare
                    val combined by lazy(LazyThreadSafetyMode.NONE) {
                        (filtered.current + lastChanges.keys)
                            .associateWith { filtered[it] + (lastChanges[it] ?: 0) }
                            // we don't want to falsely block results that end up unaffected by us
                            .filterValues { count -> count >= 1 }
                    }
                    input.filtered { mapping ->
                        val retained = mapping.value.retain(commonBindingNames).hashable()
                        if (retained.inner.count == commonBindingNames.size) {
                            // the changes should not be negative as we're not filtering anything
                            (lastChanges[retained] ?: 0) + (filtered[retained]) <= 0
                        } else {
                            // we have to check if any of our peeked changes can join with the mapping
                            // using the map is still an O(N) lookup, but it is reduced as we don't check
                            //  duplicates
                            combined.none { (blocked) -> blocked.inner.compatibleWith(mapping.value) }
                        }
                    }
                }
            }
        }

        /**
         * Filters the [input] stream, using only its processed internal state
         */
        override fun filter(input: Stream<MappingDelta>): Stream<MappingDelta> {
            // if we aren't filtering anything, we have no possible mapping we can join with,
            //  so we have no mappings we can possibly block
            if (filtered.count == 0) {
                return input
            }
            return input.filtered { mapping ->
                // filtered is strictly positive, not retaining 0-valued instances, so it being present
                //  means that it is being blocked by us
                val subset = mapping.value.retain(commonBindingNames)
                if (subset.count == commonBindingNames.size) {
                    // we can do a direct lookup
                    return@filtered subset.hashable() !in filtered
                }
                // we're dealing with a mapping that does not contain all of our common bindings, so
                //  we have to fall back to a regular 'can this join with our state' check
                // we do it through a regular join, as this can use indexes, as opposed to
                //  going through our entire internal state, which isn't indexed
                !state.join(mapping).iterator().hasNext()
            }
        }

        override fun process(delta: DataDelta) {
            // preparing the state change here, as it's likely it was already calculated before and can thus
            //  be obtained from the cache
            peekStateChange(delta)
            filtered.increment(lastChanges)
            // we now clear the change again
            lastDelta = null
            // and propagate the change to our inner state
            state.process(delta)
        }

        override fun stats(context: QueryContext, granularity: QueryStatistics.Granularity): Statistics {
            val inner = state.stats(context, granularity)
            return if (granularity isAtLeast QueryStatistics.Granularity.DETAILED) {
                Statistics.DescriptionElement(
                    inner = inner,
                    description = "FILTER NOT EXISTS\nnarrow, bindings ${commonBindingNames.asIntIterable().joinToString { bindingId -> context.resolveBinding(bindingId) }}"
                )
            } else {
                inner
            }
        }

        private var lastDelta: DataDelta? = null
        private val lastChanges = mutableMapOf<HashableMapping, Int>()

        /**
         * Peeks the impact of the [delta], putting the changes in the cached map [lastChanges]
         */
        private fun peekStateChange(delta: DataDelta) {
            if (lastDelta == delta) {
                // changes were already processed
                return
            }
            // we're overwriting the cache
            lastDelta = delta
            lastChanges.clear()
            state
                .peek(delta)
                .mapped { it.map { it.retain(commonBindingNames) } }
                .forEach { mappingDelta ->
                    when (mappingDelta) {
                        is MappingAddition -> lastChanges.replace(mappingDelta.value.hashable()) { existing -> (existing ?: 0) + 1 }
                        is MappingDeletion -> lastChanges.replace(mappingDelta.value.hashable()) { existing -> (existing ?: 0) - 1 }
                    }
                }
        }

    }

    /**
     * Special variant of the exclude filter, where discarded solutions are stored, so that incremental changes can
     *  locally look up what solutions should be allowed again.
     *
     * Required when the common bindings are not always present in a solution.
     *
     * Not a [MutableFilterState] implementor as it has a different parent - filter relationship
     */
    class Hybrid(
        /**
         * The regular query body the filter is being applied to
         */
        private val parent: MutableJoinState,
        /**
         * The state of the filter block itself
         */
        private val filter: MutableJoinState,
    ) : MutableJoinState {

        constructor(
            context: QueryContext,
            parent: MutableJoinState,
            filter: Filter.NotExists,
        ): this(
            parent = parent,
            filter = BasicGraphPatternState(
                context = context,
                ast = filter.pattern,
                externalFilters = emptyList(),
                externalBindings = BindingIdentifierSet.EMPTY,
            )
        )

        private val commonBindingNames = (filter.properties.guaranteed).intersect(parent.properties.maximum)
        // a set of mappings that were obtained from the `parent`, which are compatible with at least one of the
        //  filtered mappings above
        // TODO init blocked
        private val blocked = MappingArray(
            bindings = commonBindingNames,
            hint = MappingArrayHint(
                // checked during `init {}` as well; if this wouldn't be required, this implementation is overkill
                partialHashAccess = true
            ),
        )

        override val properties: MutableJoinState.Properties
            // we don't introduce any bindings ourselves
            get() = parent.properties

        override val cardinality: Cardinality
            get() = parent.cardinality - blocked.size

        init {
            // if `Narrow` can be used, it is preferred
            check(parent.properties.guaranteed.intersectSize(filter.properties.maximum) != filter.properties.maximum.size) { "Suboptimal filter implementation used!" }
            val filterIndexes = parent.properties.maximum.intersect(filter.properties.guaranteed)
            filter.reindex(
                bindings = filterIndexes,
                hint = MappingArrayHint(
                    partialHashAccess = filterIndexes !in parent.properties.guaranteed
                ),
            )
        }

        override fun peek(delta: DataDelta): OptimisedStream<MappingDelta> {
            // we check the impact it has on the filter
            // we collect it eagerly as we need to reuse the result
            val filterChange = filter
                .peek(delta)
                .collect()
            if (filterChange.isEmpty()) {
                // simple case: the delta only impacts our parent
                // we only need to ensure our existing state cannot possibly join with the changes the parent emits
                return parent.peek(delta)
                    .filtered { delta -> !filter.join(delta).iterator().hasNext() }
                    .optimisedForSingleUse()
            }
            // we turn the ones we filter into a change map, so we can inspect which ones have actually changed
            val filterChangeMap = filterChange
                .groupingBy { it.value.retain(commonBindingNames) }
                .fold(0) { acc, delta ->
                    val change = when (delta) {
                        is MappingAddition -> 1
                        is MappingDeletion -> -1
                    }
                    acc + change
                }
            var result: Stream<MappingDelta> = emptyStream()

            // caches the result of the cached join count to prevent duplicate work
            val existingCache = mutableMapOf<Mapping, Int>()
            fun currentBlockCount(mapping: Mapping): Int {
                return existingCache.getOrPut(mapping) {
                    filter.join(MappingAddition(mapping, delta)).count()
                }
            }

            filterChangeMap.forEach { (mapping, change) ->
                // if it ends up having no impact, there's nothing that changes here
                if (change == 0) {
                    return@forEach
                }
                // we count how many variants of this mapping are being blocked
                val existing = currentBlockCount(mapping)
                when {
                    existing == 0 -> {
                        check(change > 0)
                        // we weren't blocking it, but are now
                        val blocked = parent.join(MappingDeletion(value = mapping, origin = delta))
                        result = result.chain(blocked)
                    }
                    existing != 0 && existing + change == 0 -> {
                        // we were blocking it, but are no longer
                        // instead of letting the parent join with an addition statement, we check our internal
                        //  array - this prevents extra results from being generated in edge cases with specific
                        //  queries (e.g. certain OPTIONAL block configurations)
                        val allowed = blocked
                            .iter(mapping)
                            .filtered { it.compatibleWith(mapping) }
                            .mapped { MappingAddition(it, delta) }
                        result = result.chain(allowed)
                    }
                    // else... our filter hasn't changed in any meaningful way
                }
            }
            // it's possible the parent is not affected by the delta
            val parentChange = parent.peek(delta).collect()
            // if the parent doesn't change, there aren't any additional changes required
            if (parentChange.isEmpty()) {
                return result.optimisedForSingleUse()
            }
            // we have to append the parent's changes with our amended filter state
            result = result.chain(
                parentChange.filtered { delta ->
                    val retained = delta.value.retain(commonBindingNames)
                    val existing = currentBlockCount(retained)
                    if (retained.count != commonBindingNames.size) {
                        // we have to manually check our change set - hash lookup would fail
                        val b = filterChangeMap.asIterable().fold(0) { acc, (mapping, count) ->
                            val extra = if (mapping.compatibleWith(retained)) {
                                count
                            } else {
                                0
                            }
                            acc + extra
                        }
                        existing + b <= 0
                    } else {
                        existing + (filterChangeMap[retained] ?: 0) <= 0
                    }
                }
            )
            return result.optimisedForSingleUse()
        }

        override fun join(delta: MappingDelta): Stream<MappingDelta> {
            return parent.join(delta)
                .filtered { delta -> !filter.join(delta).iterator().hasNext() }
                .optimisedForSingleUse()
        }

        override fun reindex(
            bindings: BindingIdentifierSet,
            hint: MappingArrayHint
        ) {
            // TODO
        }

        // TODO most of this logic is a carbon copy of `peek` - can possibly be consolidated with a special return type
        //  that is then transformed to `peek` & `process`s individual needs
        override fun process(delta: DataDelta) {
            // we check the impact it has on the filter
            // we collect it eagerly as we need to reuse the result
            val filterChange = filter
                .peek(delta)
                .collect()
            if (filterChange.isEmpty()) {
                // simple case, we only have to amend our state based on parent's changes
                parent.peek(delta)
                    .filtered { delta -> filter.join(delta).iterator().hasNext() }
                    .forEach { delta ->
                        when (delta) {
                            is MappingAddition -> blocked.add(delta.value)
                            is MappingDeletion -> blocked.remove(delta.value)
                        }
                    }
            } else {
                // we turn the ones we filter into a change map, so we can inspect which ones have actually changed
                val filterChangeMap = filterChange
                    .groupingBy { it.value.retain(commonBindingNames) }
                    .fold(0) { acc, delta ->
                        val change = when (delta) {
                            is MappingAddition -> 1
                            is MappingDeletion -> -1
                        }
                        acc + change
                    }

                // caches the result of the cached join count to prevent duplicate work
                val existingCache = mutableMapOf<Mapping, Int>()
                fun currentBlockCount(mapping: Mapping): Int {
                    return existingCache.getOrPut(mapping) {
                        filter.join(MappingAddition(mapping, delta)).count()
                    }
                }

                filterChangeMap.forEach { (mapping, change) ->
                    // if it ends up having no impact, there's nothing that changes here
                    if (change == 0) {
                        return@forEach
                    }
                    // we count how many variants of this mapping are being blocked
                    val existing = currentBlockCount(mapping)
                    when {
                        existing == 0 -> {
                            check(change > 0)
                            // we weren't blocking it, but are now
                            parent
                                .join(MappingAddition(value = mapping, origin = delta))
                                .forEach { delta ->
                                    when (delta) {
                                        is MappingAddition -> blocked.add(delta.value)
                                        is MappingDeletion -> blocked.remove(delta.value)
                                    }
                                }
                        }
                        existing != 0 && existing + change == 0 -> {
                            // we were blocking it, but are no longer
                            // we simply drain all blocked mappings that match our mapping
                            blocked.removeAll(blocked.iter(mapping).collect())
                        }
                        // else... our filter hasn't changed in any meaningful way
                    }
                    // it's possible the parent is not affected by the delta
                    val parentChange = parent.peek(delta).collect()
                    // if the parent doesn't change, there aren't any additional changes required
                    if (parentChange.isNotEmpty()) {
                        // we have to append the parent's changes with our amended filter state
                        parentChange
                            // we filter to retain those that we *do not allow* so we can ammend our block list
                            .filtered { delta ->
                                val retained = delta.value.retain(commonBindingNames)
                                val existing = currentBlockCount(retained)
                                if (retained.count != commonBindingNames.size) {
                                    // we have to manually check our change set - hash lookup would fail
                                    val b = filterChangeMap.asIterable().fold(0) { acc, (mapping, count) ->
                                        val extra = if (mapping.compatibleWith(retained)) {
                                            count
                                        } else {
                                            0
                                        }
                                        acc + extra
                                    }
                                    existing + b > 0
                                } else {
                                    existing + (filterChangeMap[retained] ?: 0) > 0
                                }
                            }
                            .forEach { delta ->
                                when (delta) {
                                    is MappingAddition -> blocked.add(delta.value)
                                    is MappingDeletion -> blocked.remove(delta.value)
                                }
                            }
                    }
                }
            }
            // and we update our inner states at the end as well
            parent.process(delta)
            filter.process(delta)
        }

        override fun stats(
            context: QueryContext,
            granularity: QueryStatistics.Granularity
        ): Statistics {
            return Statistics.JoinedElement(
                left = parent.stats(context, granularity),
                right = Statistics.DescriptionElement(
                    description = "FILTER NOT EXISTS",
                    inner = filter.stats(context, granularity)
                )
            )
        }
    }

    /**
     * Special variant of the exclude filter, where the # of common bindings is zero, meaning that a satisfied internal
     *  state means no bindings are coming through
     */
    class Broad(
        private val state: MutableJoinState,
    ) : ExclusionFilterState {

        private var count = state
            .join(MappingAddition(Mapping.EMPTY, null))
            .fold(0) { acc, mappingDelta ->
                when (mappingDelta) {
                    is MappingAddition -> acc + 1
                    is MappingDeletion -> acc - 1
                }
            }

        override fun peek(delta: DataDelta): OptimisedStream<MappingDelta> {
            val change = state.peek(delta).fold(0) { acc, mappingDelta ->
                when (mappingDelta) {
                    is MappingAddition -> acc + 1
                    is MappingDeletion -> acc - 1
                }
            }
            // if the count becomes > 0 through this delta, all mappings should be removed;
            if (count == 0 && change != 0) {
                check(change > 0) { "Invalid internal state!" }
                return streamOf(MappingDeletion(Mapping.EMPTY, null))
            }
            // similarly, if the count becomes 0 through this delta, all mappings should be restored
            if (count > 0 && count + change <= 0) {
                check(count + change == 0) { "Invalid internal state!" }
                return streamOf(MappingAddition(Mapping.EMPTY, null))
            }
            // nothing changed, so the peek is empty
            return emptyStream()
        }

        override fun filter(input: Stream<MappingDelta>): Stream<MappingDelta> {
            return if (count > 0) {
                emptyStream()
            } else {
                // everything is allowed
                input
            }
        }

        override fun filter(input: Stream<MappingDelta>, delta: DataDelta): Stream<MappingDelta> {
            val change = state.peek(delta).fold(0) { acc, mappingDelta ->
                when (mappingDelta) {
                    is MappingAddition -> acc + 1
                    is MappingDeletion -> acc - 1
                }
            }
            return if (count + change > 0) {
                emptyStream()
            } else {
                // everything is allowed
                input
            }
        }

        override fun process(delta: DataDelta) {
            state.peek(delta).forEach { mappingDelta ->
                when (mappingDelta) {
                    is MappingAddition -> ++count
                    is MappingDeletion -> --count
                }
            }
            state.process(delta)
            check(count >= 0) { "Invalid internal state!" }
        }

        override fun stats(context: QueryContext, granularity: QueryStatistics.Granularity): Statistics {
            val inner = state.stats(context, granularity)
            return if (granularity isAtLeast QueryStatistics.Granularity.DETAILED) {
                Statistics.DescriptionElement(
                    inner = inner,
                    description = "FILTER NOT EXISTS\nbroad, active count $count"
                )
            } else {
                inner
            }
        }

    }

    companion object {

        operator fun invoke(context: QueryContext, parent: MutableJoinState, filter: Filter.NotExists): ExclusionFilterState {
            // we don't apply filters from our parent, as that is not the expected effect of a FILTER expression
            val state = BasicGraphPatternState(
                context = context,
                ast = filter.pattern,
                externalFilters = emptyList(),
                // we don't join with anything directly
                externalBindings = BindingIdentifierSet.EMPTY,
            )
            val externalBindings = parent.properties.maximum.intersect(state.properties.maximum)
            return if (externalBindings.isEmpty()) {
                Broad(
                    state = state,
                )
            } else {
                Narrow(
                    commonBindingNames = externalBindings,
                    state = state
                )
            }
        }

    }

}
