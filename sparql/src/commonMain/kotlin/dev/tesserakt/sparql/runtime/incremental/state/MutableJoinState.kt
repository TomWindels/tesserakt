package dev.tesserakt.sparql.runtime.incremental.state

import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.sparql.runtime.core.Mapping

/**
 * Represents a state type that can be joined with other states of the same (sub-) type, such as triple patterns or
 *  union blocks, and can process changes to the underlying data being queried.
 */
interface MutableJoinState {

    val bindings: Set<String>

    fun join(mappings: List<Mapping>): List<Mapping>

    /**
     * Returns the delta the provided [quad] produces upon insertion
     */
    fun delta(quad: Quad): List<Mapping>

    /**
     * Processes an insertion of the [quad] to this state type
     */
    fun process(quad: Quad)

}
