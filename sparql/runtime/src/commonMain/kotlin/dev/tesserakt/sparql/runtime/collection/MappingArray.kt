package dev.tesserakt.sparql.runtime.collection

import dev.tesserakt.sparql.runtime.evaluation.Mapping
import dev.tesserakt.sparql.runtime.stream.OptimisedStream
import dev.tesserakt.sparql.util.Cardinality

interface MappingArray {

    val cardinality: Cardinality

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

    fun addAll(mappings: Iterable<Mapping>)

    fun remove(mapping: Mapping)

    fun removeAll(mappings: Iterable<Mapping>)

}
