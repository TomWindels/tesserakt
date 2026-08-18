package dev.tesserakt.sparql.runtime.stream

import dev.tesserakt.sparql.runtime.collection.SimpleFixedShapeMappingArray
import dev.tesserakt.sparql.runtime.evaluation.mapping.BitsetMapping
import dev.tesserakt.sparql.runtime.evaluation.mapping.Mapping
import dev.tesserakt.sparql.util.Cardinality

/**
 * A special variant of the [Stream] type: emitted elements are only valid as long as the next
 *  element (through [Iterator.next]) hasn't been requested!
 */
class FixedShapeMappingArrayStream(
    val backing: SimpleFixedShapeMappingArray,
): FixedShapeMappingStream, OptimisedStream<Mapping> {

    override val cardinality: Cardinality
        get() = backing.cardinality

    override fun hasZeroCardinality(): Boolean {
        return backing.size == 0
    }

    override fun supportsEfficientIteration(): Boolean {
        return true
    }

    override fun supportsReuse(): Boolean {
        return true
    }

    override fun iterator(): Iterator<BitsetMapping> = object: Iterator<BitsetMapping> {

        private val inner = BitsetMapping(
            bindings = backing.bindings,
            terms = IntArray(backing.bindings.countOneBits())
        )

        private val iter = backing.backing.iterator()

        override fun hasNext(): Boolean {
            return iter.hasNext()
        }

        override fun next(): BitsetMapping {
            repeat(inner.terms.size) { i ->
                inner.terms[i] = iter.nextInt()
            }
            return inner
        }

    }

}
