package dev.tesserakt.sparql.runtime.query

import dev.tesserakt.sparql.runtime.evaluation.DataDelta
import dev.tesserakt.sparql.runtime.evaluation.MappingDelta
import dev.tesserakt.sparql.runtime.evaluation.Statistics
import dev.tesserakt.sparql.runtime.evaluation.context.QueryContext
import dev.tesserakt.sparql.runtime.stream.*
import dev.tesserakt.sparql.types.Filter
import dev.tesserakt.sparql.util.Bitmask
import kotlin.jvm.JvmInline

data class GraphPatternFilterState(
    private val stateful: Stateful,
    private val stateless: Stateless
) {

    /**
     * Peeks the total impact all filters have when applying the [delta] in this state
     */
    fun peek(parent: MutableJoinState, delta: DataDelta): Stream<MappingDelta> {
        return stateless.filter(stateful.peek(parent, delta))
    }

    /**
     * Filters the [input] stream, using only its processed internal state
     */
    fun filter(input: Stream<MappingDelta>): Stream<MappingDelta> {
        return stateless.filter(stateful.filter(input))
    }

    /**
     * Filters the [input] stream, using its processed internal state after applying the [delta]
     */
    fun filter(input: Stream<MappingDelta>, delta: DataDelta): Stream<MappingDelta> {
        return stateless.filter(stateful.filter(input, delta))
    }

    fun process(delta: DataDelta) {
        stateful.process(delta)
    }

    fun stats(context: QueryContext, base: Statistics): Statistics {
        val base = if (stateless !is Stateless.Unfiltered) {
            val inner = Statistics.SelectiveElement(inner = base, cardinality = null)
            if (Statistics.Mode isAtLeast Statistics.Mode.DETAILED) {
                val description = buildString {
                    when (stateless) {
                        is Stateless.MultiFilter -> {
                            append(stateless.filters.size)
                            append(" expression filters")
                            stateless.filters.forEach { filter ->
                                append("\n * ")
                                append(filter)
                            }
                        }
                        is Stateless.SingleFilter -> {
                            append("1 expression filter\n * ")
                            append(stateless.filter)
                        }
                        Stateless.Unfiltered -> {
                            /* cannot happen here */
                        }
                    }
                }
                Statistics.DescriptionElement(
                    inner = inner,
                    description = description,
                )
            } else {
                inner
            }
        } else {
            base
        }
        return stateful.stats(context, base)
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

        fun stats(context: QueryContext, base: Statistics): Statistics

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

            override fun stats(context: QueryContext, base: Statistics): Statistics {
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

            override fun stats(context: QueryContext, base: Statistics): Statistics {
                return Statistics.SelectiveElement(
                    inner = Statistics.JoinedElement(left = base, right = filter.stats(context)),
                    cardinality = null,
                )
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

            override fun stats(context: QueryContext, base: Statistics): Statistics {
                return filters.fold(base) { stats, filter ->
                    Statistics.SelectiveElement(
                        inner = Statistics.JoinedElement(left = stats, right = filter.stats(context)),
                        cardinality = null,
                    )
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

    sealed interface Stateless {

        fun filter(input: Stream<MappingDelta>): Stream<MappingDelta>

        data object Unfiltered: Stateless {
            override fun filter(input: Stream<MappingDelta>): Stream<MappingDelta> {
                return input
            }
        }

        @JvmInline
        value class SingleFilter(internal val filter: StatelessFilter): Stateless {

            override fun filter(input: Stream<MappingDelta>): Stream<MappingDelta> {
                return filter.filter(input)
            }

        }

        @JvmInline
        value class MultiFilter(internal val filters: CollectedStream<StatelessFilter>): Stateless {

            override fun filter(input: Stream<MappingDelta>): Stream<MappingDelta> {
                return filters.folded(input) { acc, element -> element.filter(acc) }
            }

        }

        companion object {
            operator fun invoke(filters: List<StatelessFilter>): Stateless {
                return when {
                    filters.isEmpty() -> Unfiltered
                    filters.size == 1 -> SingleFilter(filters.single())
                    else -> MultiFilter(filters.toStream())
                }
            }
        }

    }

    companion object {

        operator fun invoke(context: QueryContext, parent: GroupPatternState, filters: List<Filter>): GraphPatternFilterState {
            val stateful = mutableListOf<MutableFilterState>()
            val stateless = mutableListOf<StatelessFilter>()
            filters.forEach { filter ->
                when (filter) {
                    is Filter.Exists -> stateful.add(InclusionFilterState(context, parent, filter))
                    is Filter.NotExists -> stateful.add(ExclusionFilterState(context, parent, filter))
                    is Filter.Predicate -> stateless.add(ExpressionFilter(context, filter.expression))
                }
            }
            return GraphPatternFilterState(
                stateful = Stateful(stateful),
                stateless = Stateless(stateless)
            )
        }

    }

}
