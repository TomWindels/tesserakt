package dev.tesserakt.sparql.runtime.incremental.stream

import dev.tesserakt.sparql.runtime.incremental.types.Cardinality

internal interface Stream<out E: Any>: Iterable<E> {

    /**
     * Text description of the stream, useful for EXPLAINing the various operations executed
     */
    val description: String

    /**
     * The cardinality of this stream: a guaranteed upper bound (= worst case) number of mappings that can
     *  be iterated over
     */
    val cardinality: Cardinality

    /**
     * Analyses this stream's dependencies to detect whether its iteration can be done efficiently, which can be used
     *  to carefully buffer/collect inefficient streams when repeated iterations are planned.
     */
    fun supportsEfficientIteration(): Boolean

}
