package dev.tesserakt.sparql.runtime.query

import dev.tesserakt.sparql.QueryStatistics
import dev.tesserakt.sparql.runtime.evaluation.*
import dev.tesserakt.sparql.runtime.evaluation.context.QueryContext
import dev.tesserakt.sparql.runtime.evaluation.mapping.Mapping
import dev.tesserakt.sparql.runtime.stream.*
import dev.tesserakt.sparql.types.Filter
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
        private val state: BasicGraphPatternState,
    ) : ExclusionFilterState {

        init {
            require(commonBindingNames.isNotEmpty()) { "Invalid filter use detected!" }
        }

        // tracking what binding groups are "invalid" (= should be filtered out)
        private val filtered = Counter<Mapping>()

        override fun peek(delta: DataDelta): OptimisedStream<MappingDelta> {
            val changes = state.peek(delta).mapped { it.map { it.retain(commonBindingNames) } }
            // these changes, combined with the `filtered` state, will result in a set of bindings that can now be joined
            //  with to find all resulting changes:
            // * change additions (not in filtered now, but in `changes`) => these have to be removed outwards
            // * change deletions (in filtered now, but removed in `changes`) => these have to be added outwards
            val diff = mutableMapOf<Mapping, Int>()
            changes.forEach { mappingDelta ->
                when (mappingDelta) {
                    is MappingAddition -> diff.replace(mappingDelta.value) { (it ?: 0) + 1 }
                    is MappingDeletion -> diff.replace(mappingDelta.value) { (it ?: 0) - 1 }
                }
            }
            val results = mutableListOf<MappingDelta>()
            diff.forEach { (mapping, count) ->
                val current = filtered[mapping]
                when {
                    count < 0 && current <= -count -> {
                        // it's no longer being filtered out, meaning it's addition becomes the result of the peek
                        results.add(MappingAddition(value = mapping, origin = null))
                    }

                    count > 0 && current == 0 -> {
                        // it's now being filtered out, meaning it's removal becomes the result of the peek
                        results.add(MappingDeletion(value = mapping, origin = null))
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
                        // the changes should not be negative as we're not filtering anything
                        (lastChanges[retained] ?: 0) <= 0
                    }
                }
                lastChanges.isEmpty() -> {
                    input.filtered { mapping ->
                        val retained = mapping.value.retain(commonBindingNames)
                        filtered[retained] <= 0
                    }
                }
                else -> {
                    input.filtered { mapping ->
                        val retained = mapping.value.retain(commonBindingNames)
                        (lastChanges[retained] ?: 0) + (filtered[retained]) <= 0
                    }
                }
            }
        }

        /**
         * Filters the [input] stream, using only its processed internal state
         */
        override fun filter(input: Stream<MappingDelta>): Stream<MappingDelta> {
            return input.filtered { mapping ->
                // filtered is strictly positive, not retaining 0-valued instances, so it being present
                //  means that it is being blocked by us
                mapping.value.retain(commonBindingNames) !in filtered
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
        private val lastChanges = mutableMapOf<Mapping, Int>()

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
                        is MappingAddition -> lastChanges.replace(mappingDelta.value) { existing -> (existing ?: 0) + 1 }
                        is MappingDeletion -> lastChanges.replace(mappingDelta.value) { existing -> (existing ?: 0) - 1 }
                    }
                }
        }

    }

    /**
     * Special variant of the exclude filter, where the # of common bindings is zero, meaning that a satisfied internal
     *  state means no bindings are coming through
     */
    class Broad(private val state: BasicGraphPatternState) : ExclusionFilterState {

        private var count = 0

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
                return streamOf(MappingDeletion(state.context.emptyMapping(), null))
            }
            // similarly, if the count becomes 0 through this delta, all mappings should be restored
            if (count > 0 && count + change <= 0) {
                check(count + change == 0) { "Invalid internal state!" }
                return streamOf(MappingAddition(state.context.emptyMapping(), null))
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
            val state = BasicGraphPatternState(context, filter.pattern)
            val externalBindings = parent.bindings.intersect(state.bindings)
            return if (externalBindings.isEmpty()) {
                Broad(state = state)
            } else {
                Narrow(
                    commonBindingNames = externalBindings,
                    state = state
                )
            }
        }

    }

}
