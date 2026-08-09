package dev.tesserakt.sparql.runtime.collection

import dev.tesserakt.sparql.runtime.evaluation.BindingIdentifier
import dev.tesserakt.sparql.runtime.evaluation.BindingIdentifierSet
import dev.tesserakt.sparql.runtime.evaluation.TermIdentifier
import dev.tesserakt.sparql.runtime.evaluation.context.QueryContext
import dev.tesserakt.sparql.runtime.evaluation.mapping.Mapping
import dev.tesserakt.sparql.runtime.stream.OptimisedStream
import dev.tesserakt.sparql.runtime.stream.chain
import dev.tesserakt.sparql.runtime.stream.emptyStream
import dev.tesserakt.sparql.runtime.stream.flatMapStream

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

    private val backing = mutableMapOf<TermIdentifier?, SimpleMappingArray>()

    override val indexes: BindingIdentifierSet
        get() = BindingIdentifierSet(ids = intArrayOf(key.id))

    override var size: Int = 0
        private set

    override fun iter(mapping: Mapping): OptimisedStream<Mapping> {
        val target = mapping.get(key)
        return if (target != null) {
            val base = backing[target]?.iter() ?: emptyStream()
            // it's also possible we contain mappings that did not match our index; these
            //  can still join with the requested mapping as this would simply be a cartesian join
            val extra = backing[null]
            if (extra != null) {
                base.chain(extra.iter())
            } else {
                base
            }
        } else {
            iter()
        }
    }

    override fun iter(mappings: List<Mapping>): List<OptimisedStream<Mapping>> {
        return mappings.map { iter(it) }
    }

    override fun iter(): OptimisedStream<Mapping> {
        return backing.values.flatMapStream(cardinality)
    }

    /**
     * Adds a mapping to the backing array and indexes it accordingly.
     */
    override fun add(mapping: Mapping) {
        backing.getOrPut(
            key = mapping.get(key),
            defaultValue = { SimpleMappingArray() }
        ).add(mapping)
        size += 1
    }

    /**
     * Adds all mappings to the backing array and indexes it accordingly.
     */
    override fun addAll(mappings: Iterable<Mapping>): Int {
        var i = 0
        mappings.forEach { add(it); ++i }
        return i
    }

    override fun remove(mapping: Mapping) {
        val arr = backing[mapping.get(key)]
            ?: throw NoSuchElementException("Tried to remove $mapping, but found no backing structure for its associated index ${mapping.get(key)}")
        arr.remove(mapping)
        size -= 1
    }

    override fun removeAll(mappings: Iterable<Mapping>): Int {
        var i = 0
        mappings.forEach { remove(it); ++i }
        return i
    }

    override fun toString(): String =
        "SingleHashMappingArray (cardinality ${cardinality}, indexed on ${key})"

}
