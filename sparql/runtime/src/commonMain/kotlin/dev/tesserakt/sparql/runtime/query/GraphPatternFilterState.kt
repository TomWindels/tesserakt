package dev.tesserakt.sparql.runtime.query

import dev.tesserakt.sparql.runtime.evaluation.DataDelta
import dev.tesserakt.sparql.runtime.evaluation.MappingDelta
import dev.tesserakt.sparql.runtime.stream.*
import dev.tesserakt.sparql.types.Filter
import dev.tesserakt.sparql.util.Bitmask
import kotlin.jvm.JvmInline

sealed interface GraphPatternFilterState {

    /**
     * Peeks the total impact all filters have when applying the [delta] in this state
     */
    fun peek(parent: GroupPatternState, delta: DataDelta): Stream<MappingDelta>

    /**
     * Filters the [input] stream, using its processed internal state after applying the [delta]
     */
    fun filter(input: Stream<MappingDelta>, delta: DataDelta): Stream<MappingDelta>

    /**
     * Filters the [input] stream, using only its processed internal state
     */
    fun filter(input: Stream<MappingDelta>): Stream<MappingDelta>

    fun process(delta: DataDelta)

    fun debugInformation(): String

    data object Unfiltered: GraphPatternFilterState {

        override fun peek(parent: GroupPatternState, delta: DataDelta): Stream<MappingDelta> {
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

        override fun debugInformation(): String = "Not filtered"

    }

    @JvmInline
    value class SingleFilter(private val filter: MutableFilterState): GraphPatternFilterState {

        override fun peek(parent: GroupPatternState, delta: DataDelta): Stream<MappingDelta> {
            // getting the new results from the associated pattern group
            val one = filter.filter(parent.peek(delta), delta)
            // getting the new results from the filter, affecting the pattern group
            val two = filter.peek(delta).transform(parent.cardinality) { parent.join(it) }
            return one.chain(two)
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

        override fun debugInformation(): String = filter.debugInformation()

    }

    @JvmInline
    value class MultiFilter(private val filters: CollectedStream<MutableFilterState>): GraphPatternFilterState {

        override fun peek(parent: GroupPatternState, delta: DataDelta): Stream<MappingDelta> {
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

        override fun debugInformation(): String =
            filters
                .withIndex()
                .joinToString("\n") {
                    "* Filter #${it.index + 1}\n\t${it.value.debugInformation()}"
                }

    }

    companion object {

        operator fun invoke(parent: GroupPatternState, filters: List<Filter>): GraphPatternFilterState {
            return when {
                filters.isEmpty() -> Unfiltered

                filters.size == 1 -> SingleFilter(filters.single().createFilterState(parent))

                else -> MultiFilter(filters.map { it.createFilterState(parent) }.toStream())
            }
        }

        private fun Filter.createFilterState(parent: GroupPatternState): MutableFilterState {
            return when (this) {
                is Filter.Exists -> InclusionFilterState(parent, this)
                is Filter.NotExists -> ExclusionFilterState(parent, this)
                is Filter.Predicate -> TODO()
                is Filter.Regex -> TODO()
            }
        }

    }

}
