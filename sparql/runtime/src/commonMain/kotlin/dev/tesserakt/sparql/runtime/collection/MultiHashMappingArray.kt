package dev.tesserakt.sparql.runtime.collection

import dev.tesserakt.sparql.runtime.evaluation.BindingIdentifierSet
import dev.tesserakt.sparql.runtime.evaluation.TermIdentifierSet
import dev.tesserakt.sparql.runtime.evaluation.context.QueryContext
import dev.tesserakt.sparql.runtime.evaluation.mapping.Mapping
import dev.tesserakt.sparql.runtime.stream.OptimisedStream
import dev.tesserakt.sparql.runtime.stream.chain
import dev.tesserakt.sparql.runtime.stream.emptyStream
import dev.tesserakt.sparql.util.Cardinality

/**
 * An array useful for storing a series of mappings, capable of joining with other mappings using the hash join
 *  algorithm. Hash tables are created for every binding name passed in the constructor.
 */
class MultiHashMappingArray(
    // the binding set associated with the index
    private val indexBindingSet: BindingIdentifierSet
): MappingArray {

    constructor(
        context: QueryContext,
        bindings: Set<String>
    ): this(indexBindingSet = BindingIdentifierSet(context, bindings))

    private val backing = mutableMapOf<TermIdentifierSet, SimpleMappingArray>()

    init {
        check(indexBindingSet.size > 0) { "Invalid use of MultiHashMappingArray! No bindings are used!" }
    }

    override var cardinality = Cardinality(0)
        private set

    override fun iter(mapping: Mapping): OptimisedStream<Mapping> {
        val subset = mapping.retain(indexBindingSet)
        return iterSubset(subset)
    }

    override fun iter(mappings: List<Mapping>): List<OptimisedStream<Mapping>> {
        val subsets = mappings.map { it.retain(indexBindingSet) }
        // identical subsets can reuse the same result
        val cached = mutableMapOf<Mapping, OptimisedStream<Mapping>>()
        return subsets.map { subset ->
            cached.getOrPut(subset) { iterSubset(subset) }
        }
    }

    private fun iterSubset(subset: Mapping): OptimisedStream<Mapping> {
        // if we have a full match, a single backing collection suffices
        if (subset.count == indexBindingSet.size) {
            return backing[subset.values()]?.iter() ?: emptyStream()
        }
        // we have to manually find all relevant indexes
        var result: OptimisedStream<Mapping> = emptyStream()
        val iter = backing.iterator()
        while (iter.hasNext()) {
            val (index, arr) = iter.next()
            if (subset.compatibleWith(indexBindingSet, index)) {
                result = result.chain(arr.iter())
            }
        }
        return result
    }

    override fun iter(): OptimisedStream<Mapping> {
        var result: OptimisedStream<Mapping> = emptyStream()
        val iter = backing.values.iterator()
        while (iter.hasNext()) {
            result = result.chain(iter.next().iter())
        }
        return result
    }

    /**
     * Adds a mapping to the backing array and indexes it accordingly.
     */
    override fun add(mapping: Mapping) {
        val subset = mapping.retain(indexBindingSet)
        backing.getOrPut(subset.values()) { SimpleMappingArray() }.add(mapping)
        cardinality += 1
    }

    /**
     * Adds all mappings to the backing array and indexes it accordingly.
     */
    override fun addAll(mappings: Iterable<Mapping>) {
        mappings.forEach { add(it )}
    }

    override fun remove(mapping: Mapping) {
        backing[mapping.retain(indexBindingSet).values()]!!.remove(mapping)
        cardinality -= 1
    }

    override fun removeAll(mappings: Iterable<Mapping>) {
        mappings.forEach { remove(it )}
    }

    override fun toString(): String =
        "MultiHashMappingArray (cardinality $cardinality, indexed on ${indexBindingSet})"

}
