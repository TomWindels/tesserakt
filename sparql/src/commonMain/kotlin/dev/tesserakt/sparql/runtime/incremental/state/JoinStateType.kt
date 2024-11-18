package dev.tesserakt.sparql.runtime.incremental.state

import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.sparql.runtime.core.Mapping

/**
 * Represents a state type that can be joined with other states of the same (sub-) type, such as triple patterns or
 *  union blocks.
 */
interface JoinStateType<J: JoinStateType<J>> {

    fun join(other: J): List<Mapping>

    fun insert(quad: Quad): List<Mapping>

}
