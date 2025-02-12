package dev.tesserakt.sparql.runtime.incremental.state

import dev.tesserakt.sparql.runtime.incremental.delta.Delta
import dev.tesserakt.sparql.runtime.incremental.types.DebugWriter

/**
 * Represents a state type that can be joined with other states of the same (sub-) type, such as triple patterns or
 *  union blocks, and can process changes to the underlying data being queried.
 */
internal interface MutableJoinState {

    val bindings: Set<String>

    fun join(delta: Delta.Bindings): List<Delta.Bindings>

    /**
     * Returns the [Delta.Bindings] changes that occur when [process]ing the [delta] in this state, without
     *  actually modifying it (see [process] for mutating the state)
     */
    fun peek(delta: Delta.Data): List<Delta.Bindings>

    /**
     * Updates the internal state according to the [delta] change.
     */
    fun process(delta: Delta.Data)

    fun debugInformation(writer: DebugWriter)

}
