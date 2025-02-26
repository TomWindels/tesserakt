package dev.tesserakt.sparql.runtime.incremental.collection

import dev.tesserakt.sparql.runtime.core.Mapping
import dev.tesserakt.sparql.runtime.incremental.stream.Stream

internal interface MappingArray {

    val mappings: List<Mapping>

    val cardinality: Int

    /**
     * Returns a [Stream] of [Mapping]s that are likely (but not guaranteed to be!) compatible with
     *  the provided [mapping], which can be used to create joined mappings.
     */
    fun iter(mapping: Mapping): Stream<Mapping>

    /**
     * Returns a list of [Stream]s that yield [Mapping]s that are likely (but not guaranteed to be!) compatible with
     *  the provided [mappings] at their respective index, which can be used to create joined mappings.
     */
    fun iter(mappings: List<Mapping>): List<Stream<Mapping>>

    fun add(mapping: Mapping)

    fun addAll(mappings: Iterable<Mapping>)

    fun remove(mapping: Mapping)

    fun removeAll(mappings: Iterable<Mapping>)

}
