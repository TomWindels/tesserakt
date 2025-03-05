package dev.tesserakt.sparql.runtime.incremental.stream


/**
 * Marker type, used to denote streams that can be considered fast iteration-wise; enforced in use cases that are known
 *  to be frequently iterated over. A side effect of its typically efficient nature is that the estimated cardinality
 *  is much closer to the actual cardinality.
 */
internal interface OptimisedStream<E: Any>: Stream<E> {

    // this is this type's whole point
    override fun supportsEfficientIteration() = true

}
