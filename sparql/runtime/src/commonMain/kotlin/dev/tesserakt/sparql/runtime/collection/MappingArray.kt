package dev.tesserakt.sparql.runtime.collection

import dev.tesserakt.sparql.runtime.evaluation.BindingIdentifierSet
import dev.tesserakt.sparql.runtime.evaluation.mapping.Mapping
import dev.tesserakt.sparql.runtime.stream.OptimisedStream
import dev.tesserakt.sparql.util.Cardinality

interface MappingArray : Iterable<Mapping> {

    val size: Int

    val cardinality: Cardinality
        get() = Cardinality(size)

    val indexes: BindingIdentifierSet

    /**
     * Returns an [OptimisedStream] of [Mapping]s that are present inside this structure
     */
    fun iter(): OptimisedStream<Mapping>

    /**
     * Returns an [OptimisedStream] of [Mapping]s that are likely (but not guaranteed to be!) compatible with
     *  the provided [mapping], which can be used to create joined mappings.
     */
    fun iter(mapping: Mapping): OptimisedStream<Mapping>

    /**
     * Returns a list of [OptimisedStream]s that yield [Mapping]s that are likely (but not guaranteed to be!) compatible with
     *  the provided [mappings] at their respective index, which can be used to create joined mappings.
     */
    fun iter(mappings: List<Mapping>): List<OptimisedStream<Mapping>>

    fun add(mapping: Mapping)

    /**
     * Adds all [mappings] into the backing structure, returning the number of elements that were added
     */
    fun addAll(mappings: Iterable<Mapping>): Int

    fun remove(mapping: Mapping)

    /**
     * Removes all [mappings] from the backing structure, returning the number of elements that were removed
     */
    fun removeAll(mappings: Iterable<Mapping>): Int

    override fun iterator(): Iterator<Mapping> {
        return iter().iterator()
    }

}
