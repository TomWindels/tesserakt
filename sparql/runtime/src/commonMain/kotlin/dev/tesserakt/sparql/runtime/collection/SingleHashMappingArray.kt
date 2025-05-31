package dev.tesserakt.sparql.runtime.collection

import dev.tesserakt.sparql.runtime.evaluation.BindingIdentifier
import dev.tesserakt.sparql.runtime.evaluation.TermIdentifier
import dev.tesserakt.sparql.runtime.evaluation.context.QueryContext
import dev.tesserakt.sparql.runtime.evaluation.mapping.Mapping
import dev.tesserakt.sparql.runtime.stream.*
import dev.tesserakt.sparql.util.Cardinality

/**
 * An array useful for storing a series of mappings, capable of joining with other mappings using the hash join
 *  algorithm. Hash tables are created for every binding name passed in the constructor.
 */
class SingleHashMappingArray(
    private val key: BindingIdentifier
): MappingArray {

    constructor(
        context: QueryContext,
        binding: String,
    ): this(
        key = BindingIdentifier(context, binding)
    )

    private val backing = mutableMapOf<TermIdentifier, SimpleMappingArray>()

    override var cardinality = Cardinality(0)
        private set

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
            iter()
        }
    }

    override fun iter(mappings: List<Mapping>): List<OptimisedStream<Mapping>> {
        return mappings.map { iter(it) }
    }

    override fun iter(): OptimisedStream<Mapping> {
        // a series of chains are required for all available mappings as there's no index that can
        //  be used
        return if (backing.isEmpty()) {
            emptyStream()
        } else {
            OptimisedStreamView(backing.values.toStream().transform(cardinality.value / backing.values.size) { it.iter() })
        }
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
        cardinality += 1
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
        cardinality -= 1
    }

    override fun removeAll(mappings: Iterable<Mapping>) {
        mappings.forEach { remove(it) }
    }

    override fun toString(): String =
        "SingleHashMappingArray (cardinality ${cardinality}, indexed on ${key})"

}
