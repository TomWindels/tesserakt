package dev.tesserakt.sparql.runtime.query

import dev.tesserakt.sparql.QueryStatistics
import dev.tesserakt.sparql.runtime.evaluation.DataDelta
import dev.tesserakt.sparql.runtime.evaluation.MappingDelta
import dev.tesserakt.sparql.runtime.evaluation.Statistics
import dev.tesserakt.sparql.runtime.evaluation.context.QueryContext
import dev.tesserakt.sparql.runtime.stream.*
import dev.tesserakt.sparql.types.Filter
import dev.tesserakt.sparql.util.Bitmask
import kotlin.jvm.JvmInline

@JvmInline
value class GraphPatternFilterState(
    private val stateful: Stateful,
) {

    /**
     * Peeks the total impact all filters have when applying the [delta] in this state
     */
    fun peek(parent: MutableJoinState, delta: DataDelta): Stream<MappingDelta> {
        return stateful.peek(parent, delta)
    }

    /**
     * Filters the [input] stream, using only its processed internal state
     */
    fun filter(input: Stream<MappingDelta>): Stream<MappingDelta> {
        return stateful.filter(input)
    }

    /**
     * Filters the [input] stream, using its processed internal state after applying the [delta]
     */
    fun filter(input: Stream<MappingDelta>, delta: DataDelta): Stream<MappingDelta> {
        return stateful.filter(input, delta)
    }

    fun process(delta: DataDelta) {
        stateful.process(delta)
    }

    fun stats(context: QueryContext, base: Statistics, granularity: QueryStatistics.Granularity): Statistics {
        return stateful.stats(context, base, granularity)
    }

    sealed interface Stateful {

        /**
         * Peeks the total impact all filters have when applying the [delta] in this state
         */
        fun peek(parent: MutableJoinState, delta: DataDelta): Stream<MappingDelta>

        /**
         * Filters the [input] stream, using its processed internal state after applying the [delta]
         */
        fun filter(input: Stream<MappingDelta>, delta: DataDelta): Stream<MappingDelta>

        /**
         * Filters the [input] stream, using only its processed internal state
         */
        fun filter(input: Stream<MappingDelta>): Stream<MappingDelta>

        fun process(delta: DataDelta)

        fun stats(context: QueryContext, base: Statistics, granularity: QueryStatistics.Granularity): Statistics

        data object Unfiltered: Stateful {

            override fun peek(parent: MutableJoinState, delta: DataDelta): Stream<MappingDelta> {
                // no filters applied
                return parent.peek(delta)
            }

            override fun filter(input: Stream<MappingDelta>, delta: DataDelta): Stream<MappingDelta> {
                // can go through unfiltered
                return input
            }

            override fun filter(input: Stream<MappingDelta>): Stream<MappingDelta> {
                // can go through unfiltered
                return input
            }

            override fun process(delta: DataDelta) {
                // nothing to do, no filters applicable
            }

            override fun stats(context: QueryContext, base: Statistics, granularity: QueryStatistics.Granularity): Statistics {
                return base
            }

        }

        @JvmInline
        value class SingleFilter(private val filter: MutableFilterState): Stateful {

            override fun peek(parent: MutableJoinState, delta: DataDelta): Stream<MappingDelta> {
                // getting the new results from the associated pattern group
                val one = filter.filter(parent.peek(delta), delta)
                // getting the new results from the filter, affecting the pattern group
                val two = filter.peek(delta).transform(parent.cardinality) { parent.join(it) }
                return two.chain(one)
            }

            override fun filter(input: Stream<MappingDelta>, delta: DataDelta): Stream<MappingDelta> {
                return filter.filter(input, delta)
            }

            override fun filter(input: Stream<MappingDelta>): Stream<MappingDelta> {
                return filter.filter(input)
            }

            override fun process(delta: DataDelta) {
                filter.process(delta)
            }

            override fun stats(context: QueryContext, base: Statistics, granularity: QueryStatistics.Granularity): Statistics {
                return Statistics.JoinedElement(left = base, right = filter.stats(context, granularity))
            }

        }

        @JvmInline
        value class MultiFilter(private val filters: CollectedStream<MutableFilterState>): Stateful {

            override fun peek(parent: MutableJoinState, delta: DataDelta): Stream<MappingDelta> {
                // getting the new results from the associated pattern group
                val one = filters.folded(parent.peek(delta)) { results, filter ->
                    filter.filter(results, delta = delta)
                }
                // getting the new results from the filters, affecting the pattern group
                val two = filters
                    .zippedWithIndex()
                    .merge { (i, exclude) ->
                        val base = exclude.peek(delta).transform(parent.cardinality) { parent.join(it) }
                        // all `excluded` minus element `i` still have to filter these result changes
                        Bitmask.onesAt(i, length = filters.size)
                            .inv()
                            .toStream(filters.size - 1)
                            .mapped { filters[it] }
                            .folded(base) { results, filter -> filter.filter(results, delta = delta) }
                    }
                return one.chain(two)
            }

            override fun filter(input: Stream<MappingDelta>, delta: DataDelta): Stream<MappingDelta> {
                return filters.folded(input) { results, filter -> filter.filter(results, delta = delta) }
            }

            override fun filter(input: Stream<MappingDelta>): Stream<MappingDelta> {
                return filters.folded(input) { results, filter -> filter.filter(results) }
            }

            override fun process(delta: DataDelta) {
                filters.forEach { it.process(delta) }
            }

            override fun stats(context: QueryContext, base: Statistics, granularity: QueryStatistics.Granularity): Statistics {
                return filters.fold(base) { stats, filter ->
                    Statistics.JoinedElement(left = stats, right = filter.stats(context, granularity))
                }
            }

        }

        companion object {
            operator fun invoke(filters: List<MutableFilterState>): Stateful {
                return when {
                    filters.isEmpty() -> Unfiltered
                    filters.size == 1 -> SingleFilter(filters.single())
                    else -> MultiFilter(filters.toStream())
                }
            }
        }

    }

    companion object {

        /**
         * Constructs a [GraphPatternFilterState], responsible for filtering changes encountered by a [parent]
         *  [MutableJoinState] instance (typically a [GroupPatternState]) through the use of an internal state.
         * The types of filters supported by this type are **only** the **stateful** types:
         *  * `FILTER EXISTS` ('inclusion filters')
         *  * `FILTER NOT EXISTS` ('exclusion filters')
         */
        operator fun invoke(context: QueryContext, parent: MutableJoinState, filters: List<Filter>): GraphPatternFilterState {
            val stateful = mutableListOf<MutableFilterState>()
            filters.forEach { filter ->
                when (filter) {
                    is Filter.Exists -> stateful.add(InclusionFilterState(context, parent, filter))
                    is Filter.NotExists -> stateful.add(ExclusionFilterState(context, parent, filter))
                    is Filter.Predicate -> {
                        // nothing to do - not our responsibility
                    }
                }
            }
            return GraphPatternFilterState(
                stateful = Stateful(stateful),
            )
        }

    }

}
