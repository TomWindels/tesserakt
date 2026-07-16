package dev.tesserakt.sparql.runtime.stream

import dev.tesserakt.sparql.util.Cardinality
import kotlin.jvm.JvmInline


/**
 * A wrapper type to map an inner stream to itself, but signaling it as being optimised type-wise. This has two main
 *  use cases:
 *  * stream types not necessarily optimised marking as optimised when their operator chain is considered optimised
 *    (= calling `supportsEfficientIteration()` returns true)
 *  * stream types not necessarily optimised marking as optimised when smaller memory footprints are preferred over
 *    raw performance obtained by buffering it
 */
@JvmInline
value class OptimisedStreamView<E: Any>(val input: Stream<E>): Stream<E>, OptimisedStream<E> {

    override val cardinality: Cardinality
        get() = input.cardinality

    override fun hasZeroCardinality(): Boolean {
        return input.hasZeroCardinality()
    }

    override fun supportsReuse(): Boolean {
        return input.supportsReuse()
    }

    override fun iterator(): Iterator<E> {
        return input.iterator()
    }

    override fun supportsEfficientIteration(): Boolean {
        return true
    }

}
