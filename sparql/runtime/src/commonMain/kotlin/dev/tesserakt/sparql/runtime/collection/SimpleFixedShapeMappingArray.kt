package dev.tesserakt.sparql.runtime.collection

import dev.tesserakt.sparql.runtime.collection.integer.DynamicIntArray
import dev.tesserakt.sparql.runtime.evaluation.BindingIdentifierSet
import dev.tesserakt.sparql.runtime.evaluation.mapping.BitsetMapping
import dev.tesserakt.sparql.runtime.evaluation.mapping.Mapping
import dev.tesserakt.sparql.runtime.stream.FixedShapeMappingArrayStream
import dev.tesserakt.sparql.runtime.stream.OptimisedStream
import dev.tesserakt.sparql.util.Cardinality

/**
 * A specialised variant of the [SimpleMappingArray] that 'assumes' all mappings to
 *  be of the same 'shape'. This means that all incoming mappings have the same exact
 *  bindings set, which allows for optimizations w.r.t. storing & joining mappings.
 *
 * Can only be used with [dev.tesserakt.sparql.runtime.evaluation.mapping.BitsetMapping] instances
 */
class SimpleFixedShapeMappingArray(
    /**
     * The same mask used in [dev.tesserakt.sparql.runtime.evaluation.mapping.BitsetMapping]
     */
    internal val bindings: Int,
    internal val backing: DynamicIntArray = DynamicIntArray(),
): MappingArray {

    override val cardinality: Cardinality
        get() = Cardinality(backing.size / bindings.countOneBits())

    override val indexes: BindingIdentifierSet
        get() = BindingIdentifierSet.EMPTY

    val size get() = backing.size

    override fun iter(mappings: List<Mapping>): List<OptimisedStream<Mapping>> {
        // the parameter is unused as we're not indexed
        val stream = iter()
        return List(mappings.size) { stream }
    }

    override fun iter(mapping: Mapping): OptimisedStream<Mapping> {
        // the parameter is unused as we're not indexed
        return iter()
    }

    override fun iter(): OptimisedStream<Mapping> {
        return FixedShapeMappingArrayStream(this)
    }

    override fun add(mapping: Mapping) {
        if (mapping !is BitsetMapping) {
            throw IllegalArgumentException()
        }
        if (mapping.bindings != bindings) {
            throw IllegalArgumentException("Binding mismatch occurred: got ${mapping.bindings}, expected $bindings")
        }
        backing.addAll(mapping.terms)
    }

    override fun addAll(mappings: Iterable<Mapping>): Int {
        val current = this.backing.size
        mappings.forEach { add(it) }
        return (this.backing.size - current) / bindings.countOneBits()
    }

    override fun remove(mapping: Mapping) {
        if (mapping !is BitsetMapping) {
            throw IllegalArgumentException()
        }
        if (mapping.bindings != bindings) {
            throw IllegalArgumentException("Binding mismatch occurred: got ${mapping.bindings}, expected $bindings")
        }
        val stepSize = bindings.countOneBits()
        var i = backing.size - stepSize
        while (i > 0) {
            var j = 0
            while (j < stepSize && backing[i + j] == mapping.terms[j]) {
                ++j
            }
            if (j == stepSize) {
                break
            }
            i -= stepSize
        }
        if (i < 0) {
            throw NoSuchElementException("$mapping cannot be removed from SimpleFixedShapeMappingArray - not found!")
        }
        backing.swapRemoveRange(start = i, end = i + stepSize)
    }

    override fun removeAll(mappings: Iterable<Mapping>): Int {
        val previous = this.backing.size
        mappings.forEach(::remove)
        return (this.backing.size - previous) / bindings.countOneBits()
    }

    override fun toString() = "SimpleFixedShapeMappingArray (bindings: 0x${bindings.toHexString(format = HexFormat { upperCase = true })}, cardinality ${cardinality})"

}
