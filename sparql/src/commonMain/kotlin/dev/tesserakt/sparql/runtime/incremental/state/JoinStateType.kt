package dev.tesserakt.sparql.runtime.incremental.state

import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.sparql.runtime.core.Mapping

/**
 * Represents a state type that can be joined with other states of the same (sub-) type, such as triple patterns or
 *  union blocks.
 */
interface JoinStateType {

    val bindings: Set<String>

    fun join(mappings: List<Mapping>): List<Mapping>

    fun insert(quad: Quad): List<Mapping>

}
