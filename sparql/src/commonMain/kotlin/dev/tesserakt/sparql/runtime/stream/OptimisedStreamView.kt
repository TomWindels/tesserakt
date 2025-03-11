package dev.tesserakt.sparql.runtime.stream

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
internal value class OptimisedStreamView<E: Any>(val input: Stream<E>): Stream<E> by input, OptimisedStream<E> {

    override val description: String
        get() = "OptimisedView[${input.description}]"

    override fun supportsEfficientIteration(): Boolean {
        return true
    }

}
