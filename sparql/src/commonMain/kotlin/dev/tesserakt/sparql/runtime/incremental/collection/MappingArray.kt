package dev.tesserakt.sparql.runtime.incremental.collection

import dev.tesserakt.sparql.runtime.core.Mapping

internal interface MappingArray {

    val mappings: List<Mapping>

    /**
     * Returns an [Iterable] that yield [Mapping]s that are likely (but not guaranteed to be!) compatible with
     *  the provided [mapping], which can be used to create joined mappings.
     */
    fun iter(mapping: Mapping): Iterable<Mapping>

    /**
     * Returns a list of [Iterable]s that yield [Mapping]s that are likely (but not guaranteed to be!) compatible with
     *  the provided [mappings] at their respective index, which can be used to create joined mappings.
     */
    fun iter(mappings: List<Mapping>): List<Iterable<Mapping>>

    fun add(mapping: Mapping)

    fun addAll(mappings: Collection<Mapping>)

    fun remove(mapping: Mapping)

    fun removeAll(mappings: Collection<Mapping>)

}
