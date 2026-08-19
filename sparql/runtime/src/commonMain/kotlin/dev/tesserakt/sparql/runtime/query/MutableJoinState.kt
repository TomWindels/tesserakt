package dev.tesserakt.sparql.runtime.query

import dev.tesserakt.sparql.QueryStatistics
import dev.tesserakt.sparql.runtime.collection.MappingArrayHint
import dev.tesserakt.sparql.runtime.evaluation.BindingIdentifierSet
import dev.tesserakt.sparql.runtime.evaluation.DataDelta
import dev.tesserakt.sparql.runtime.evaluation.MappingDelta
import dev.tesserakt.sparql.runtime.evaluation.Statistics
import dev.tesserakt.sparql.runtime.evaluation.context.QueryContext
import dev.tesserakt.sparql.runtime.stream.OptimisedStream
import dev.tesserakt.sparql.runtime.stream.Stream
import dev.tesserakt.sparql.util.Cardinality

/**
 * Represents a state type that can be joined with other states of the same (sub-) type, such as triple patterns or
 *  union blocks, and can process changes to the underlying data being queried.
 */
interface MutableJoinState {

    /**
     * Information about a [MutableJoinState]'s bindings that are bound for [MappingDelta]s emitted whilst [process]ing.
     *  For most query structures, the [guaranteed] set is identical to the [maximum] set. Notable exceptions are:
     *  * `OPTIONAL` blocks, as these only emit additional values if matched, or the original value if unmatched;
     *  * `UNION`s, as these emit solutions for all segments, which can refer to different bindings
     */
    data class Properties(
        /**
         * Set of bindings *guaranteed* to be bound for any given [MappingDelta] emitted by [process]
         */
        val guaranteed: BindingIdentifierSet,
        /**
         * Set of bindings *possibly* bound for any given [MappingDelta] emitted by [process]
         */
        val maximum: BindingIdentifierSet,
    ) {

        constructor(exact: BindingIdentifierSet): this(guaranteed = exact, maximum = exact)

        init {
            check(guaranteed in maximum) { "Found bindings that are set as 'guaranteed', but are not listed as 'maximum': $guaranteed - $maximum" }
        }

        companion object {
            val EMPTY = Properties(exact = BindingIdentifierSet.EMPTY)
        }

    }

    val properties: Properties

    /**
     * Denotes the number of matches it contains, useful for quick cardinality calculations (e.g., joining this state
     *  on an empty solution results in [cardinality] results, or a size of 0 guarantees no results will get generated)
     */
    val cardinality: Cardinality

    fun join(delta: MappingDelta): Stream<MappingDelta>

    /**
     * Passes a hint down the underlying memory structure to optimise subsequent [join] executions. Depending
     *  on the underlying type, this hint may be ignored. Requesting a rehash on bindings not found in the [bindings]
     *  collection for this state is not useful.
     */
    fun reindex(bindings: BindingIdentifierSet, hint: MappingArrayHint)

    /**
     * Enqueues a change to the underlying data. Output changes are only made visible after calling [process].
     */
    fun enqueue(delta: DataDelta)

    /**
     * Updates the state according to the [enqueue]d changes, returning the set of output changes that happened because
     *  of them.
     *
     * Note that empty results are emitted if no changes were enqueued compared to the last [process] call.
     */
    fun process(): OptimisedStream<MappingDelta>

    fun stats(context: QueryContext, granularity: QueryStatistics.Granularity): Statistics

}
