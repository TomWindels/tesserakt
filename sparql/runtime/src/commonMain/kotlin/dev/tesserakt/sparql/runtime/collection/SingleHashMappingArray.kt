package dev.tesserakt.sparql.runtime.collection

import dev.tesserakt.sparql.runtime.evaluation.BindingIdentifier
import dev.tesserakt.sparql.runtime.evaluation.Mapping
import dev.tesserakt.sparql.runtime.evaluation.QueryContext
import dev.tesserakt.sparql.runtime.evaluation.TermIdentifier
import dev.tesserakt.sparql.runtime.stream.OptimisedStream
import dev.tesserakt.sparql.runtime.stream.chain
import dev.tesserakt.sparql.runtime.stream.emptyStream
import dev.tesserakt.sparql.util.Cardinality

/**
 * An array useful for storing a series of mappings, capable of joining with other mappings using the hash join
 *  algorithm. Hash tables are created for every binding name passed in the constructor.
 */
class SingleHashMappingArray(
    context: QueryContext,
    binding: String
): MappingArray {

    private val key = BindingIdentifier(context, binding)
    private val backing = mutableMapOf<TermIdentifier, SimpleMappingArray>()

    override val cardinality: Cardinality
        get() = Cardinality(backing.asIterable().sumOf { it.value.cardinality.value })

    /**
     * Denotes the number of matches it contains, useful for quick cardinality calculations (e.g., joining this state
     *  on an empty solution results in [size] results, or a size of 0 guarantees no results will get generated)
     */
    val size: Int get() = backing.size

    override fun iter(mapping: Mapping): OptimisedStream<Mapping> {
        val target = mapping.get(key)
        return if (target != null) {
            backing[target]?.iter() ?: emptyStream()
        } else {
            // a series of chains are required for all available mappings as there's no index that can
            //  be used
            val iter = backing.values.iterator()
            if (!iter.hasNext()) {
                emptyStream()
            } else {
                var result: OptimisedStream<Mapping> = iter.next().iter()
                while (iter.hasNext()) {
                    result = result.chain(iter.next().iter())
                }
                result
            }
        }
    }

    override fun iter(mappings: List<Mapping>): List<OptimisedStream<Mapping>> {
        return mappings.map { iter(it) }
    }

    /**
     * Adds a mapping to the backing array and indexes it accordingly.
     */
    override fun add(mapping: Mapping) {
        backing.getOrPut(
            key = mapping.get(key)
                ?: throw IllegalArgumentException("Mapping $mapping has no value required for index `${key}`"),
            defaultValue = { SimpleMappingArray() }
        ).add(mapping)
    }

    /**
     * Adds all mappings to the backing array and indexes it accordingly.
     */
    override fun addAll(mappings: Iterable<Mapping>) {
        mappings.forEach { add(it) }
    }

    override fun remove(mapping: Mapping) {
        backing
            .get(mapping.get(key) ?: throw IllegalArgumentException("Mapping $mapping has no value required for index `${key}`"))!!
            .remove(mapping)
    }

    override fun removeAll(mappings: Iterable<Mapping>) {
        mappings.forEach { remove(it) }
    }

    override fun toString(): String =
        "SingleHashMappingArray (cardinality ${cardinality}, indexed on ${key})"

}
