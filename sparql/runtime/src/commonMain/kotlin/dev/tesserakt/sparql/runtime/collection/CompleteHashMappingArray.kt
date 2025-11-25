package dev.tesserakt.sparql.runtime.collection

import dev.tesserakt.sparql.runtime.evaluation.BindingIdentifierSet
import dev.tesserakt.sparql.runtime.evaluation.TermIdentifierSet
import dev.tesserakt.sparql.runtime.evaluation.context.QueryContext
import dev.tesserakt.sparql.runtime.evaluation.mapping.Mapping
import dev.tesserakt.sparql.runtime.stream.OptimisedStream
import dev.tesserakt.sparql.runtime.stream.chain
import dev.tesserakt.sparql.runtime.stream.emptyStream
import dev.tesserakt.sparql.util.Cardinality
import dev.tesserakt.util.replace

/**
 * A special [MappingArray], acting as a [MultiHashMappingArray] that only allows full-index access with increased
 *  performance. Throws [UnsupportedOperationException] when attempting access using a partial index.
 */
class CompleteHashMappingArray(
    // the binding set associated with the index
    private val indexBindingSet: BindingIdentifierSet
): MappingArray {

    private val backing = mutableMapOf<TermIdentifierSet, SimpleMappingArray>()

    override var cardinality: Cardinality = Cardinality(0)
    private set

    override val indexes: BindingIdentifierSet
        get() = indexBindingSet

    constructor(
        context: QueryContext,
        bindings: Set<String>
    ): this(indexBindingSet = BindingIdentifierSet(context, bindings))

    init {
        check(indexBindingSet.size > 1) {
            "Invalid use of CompleteHashMappingArray! At least 2 index bindings have to be configured!"
        }
    }

    override fun iter(): OptimisedStream<Mapping> {
        val iter = backing.iterator()
        if (!iter.hasNext()) {
            return emptyStream()
        }
        var result: OptimisedStream<Mapping> = iter.next().value.iter()
        while (iter.hasNext()) {
            result = result.chain(iter.next().value.iter())
        }
        return result
    }

    override fun iter(mapping: Mapping): OptimisedStream<Mapping> {
        val hash = mapping.retain(indexBindingSet).values()
        if (hash.size == 0) {
            // special case; we can return the entire `iter` result
            return iter()
        }
        if (hash.size != indexBindingSet.size) {
            // a partial hash access is attempted, which is not supported
            //  as it would fall back to a full scan
            throw UnsupportedOperationException("Partial hash for mapping is not supported!")
        }
        return backing[hash]?.iter() ?: return emptyStream()
    }

    override fun iter(mappings: List<Mapping>): List<OptimisedStream<Mapping>> {
        return mappings.map { iter(it) }
    }

    override fun add(mapping: Mapping) {
        val hash = mapping.retain(indexBindingSet).values()
        if (hash.size != indexBindingSet.size) {
            // a partial hash access is attempted, which is not supported
            //  as it would fall back to a full scan
            throw UnsupportedOperationException("Partial hash for mapping is not supported!")
        }
        val arr = backing.getOrPut(hash) { SimpleMappingArray() }
        arr.add(mapping)
        cardinality += 1
    }

    override fun addAll(mappings: Iterable<Mapping>) {
        if (mappings is Collection<Mapping>) {
            mappings.forEach { mapping ->
                val hash = mapping.retain(indexBindingSet).values()
                if (hash.size != indexBindingSet.size) {
                    // a partial hash access is attempted, which is not supported
                    //  as it would fall back to a full scan
                    throw UnsupportedOperationException("Partial hash for mapping is not supported!")
                }
                val arr = backing.getOrPut(hash) { SimpleMappingArray() }
                arr.add(mapping)
            }
            cardinality += mappings.size
        } else {
            mappings.forEach { add(it) }
        }
    }

    override fun remove(mapping: Mapping) {
        val hash = mapping.retain(indexBindingSet).values()
        if (hash.size != indexBindingSet.size) {
            // a partial hash access is attempted, which is not supported
            //  as it would fall back to a full scan
            throw UnsupportedOperationException("Partial hash for mapping is not supported!")
        }
        backing.replace(hash) { arr ->
            if (arr == null) {
                throw NoSuchElementException()
            }
            arr.remove(mapping)
            if (arr.size == 0) {
                null
            } else {
                arr
            }
        }
        cardinality -= 1
    }

    override fun removeAll(mappings: Iterable<Mapping>) {
        if (mappings is Collection<Mapping>) {
            mappings.forEach { mapping ->
                val hash = mapping.retain(indexBindingSet).values()
                if (hash.size != indexBindingSet.size) {
                    // a partial hash access is attempted, which is not supported
                    //  as it would fall back to a full scan
                    throw UnsupportedOperationException("Partial hash for mapping is not supported!")
                }
                backing.replace(hash) { arr ->
                    if (arr == null) {
                        throw NoSuchElementException()
                    }
                    arr.remove(mapping)
                    if (arr.size == 0) {
                        null
                    } else {
                        arr
                    }
                }
            }
            cardinality -= mappings.size
        } else {
            mappings.forEach { remove(it) }
        }
    }

}
