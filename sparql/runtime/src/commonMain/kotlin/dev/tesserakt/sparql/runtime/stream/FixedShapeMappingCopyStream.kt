package dev.tesserakt.sparql.runtime.stream

import dev.tesserakt.sparql.runtime.evaluation.mapping.BitsetMapping
import dev.tesserakt.sparql.runtime.evaluation.mapping.Mapping
import dev.tesserakt.sparql.util.Cardinality

/**
 * Converts the inner [FixedShapeMappingStream], which are known to reuse mapping instances, to a stream
 *  that copies the results to distinct mapping instances
 */
class FixedShapeMappingCopyStream(val inner: FixedShapeMappingStream): Stream<Mapping> {

    override val cardinality: Cardinality
        get() = inner.cardinality

    override fun hasZeroCardinality(): Boolean {
        return inner.hasZeroCardinality()
    }

    override fun supportsEfficientIteration(): Boolean {
        return inner.supportsEfficientIteration()
    }

    override fun supportsReuse(): Boolean {
        return inner.supportsReuse()
    }

    override fun iterator(): Iterator<Mapping> {
        return object : Iterator<Mapping> {

            private val iter = inner.iterator()

            override fun next(): Mapping {
                // we create a deep copy of the object
                val value = iter.next() as BitsetMapping
                return BitsetMapping(
                    bindings = value.bindings,
                    terms = value.terms.copyOf(),
                )
            }

            override fun hasNext(): Boolean {
                return iter.hasNext()
            }

        }
    }

}
