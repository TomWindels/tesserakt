package dev.tesserakt.sparql.runtime.query

import dev.tesserakt.sparql.runtime.evaluation.DataDelta
import dev.tesserakt.sparql.runtime.evaluation.MappingDelta
import dev.tesserakt.sparql.runtime.stream.OptimisedStream
import dev.tesserakt.sparql.runtime.stream.Stream
import dev.tesserakt.sparql.util.Cardinality

/**
 * Represents a state type that can be joined with other states of the same (sub-) type, such as triple patterns or
 *  union blocks, and can process changes to the underlying data being queried.
 */
interface MutableJoinState {

    val bindings: Set<String>

    /**
     * Denotes the number of matches it contains, useful for quick cardinality calculations (e.g., joining this state
     *  on an empty solution results in [cardinality] results, or a size of 0 guarantees no results will get generated)
     */
    val cardinality: Cardinality

    fun join(delta: MappingDelta): Stream<MappingDelta>

    /**
     * Returns the [MappingDelta] changes that occur when [process]ing the [delta] in this state, without
     *  actually modifying it (see [process] for mutating the state)
     */
    fun peek(delta: DataDelta): OptimisedStream<MappingDelta>

    /**
     * Updates the state according to the [delta] change.
     */
    fun process(delta: DataDelta)

}
