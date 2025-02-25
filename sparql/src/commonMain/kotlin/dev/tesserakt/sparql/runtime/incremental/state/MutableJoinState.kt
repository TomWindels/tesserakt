package dev.tesserakt.sparql.runtime.incremental.state

import dev.tesserakt.sparql.runtime.incremental.delta.DataDelta
import dev.tesserakt.sparql.runtime.incremental.delta.MappingDelta

/**
 * Represents a state type that can be joined with other states of the same (sub-) type, such as triple patterns or
 *  union blocks, and can process changes to the underlying data being queried.
 */
internal interface MutableJoinState {

    val bindings: Set<String>

    fun join(delta: MappingDelta): List<MappingDelta>

    /**
     * Returns the [MappingDelta] changes that occur when [process]ing the [delta] in this state, without
     *  actually modifying it (see [process] for mutating the state)
     */
    fun peek(delta: DataDelta): List<MappingDelta>

    /**
     * Updates the internal state according to the [delta] change.
     */
    fun process(delta: DataDelta)

    fun debugInformation(): String = toString()

}
