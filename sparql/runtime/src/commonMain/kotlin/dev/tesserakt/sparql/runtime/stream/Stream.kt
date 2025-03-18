package dev.tesserakt.sparql.runtime.stream

import dev.tesserakt.sparql.util.Cardinality

interface Stream<out E: Any>: Iterable<E> {

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

    /**
     * Analyses this stream's dependencies to detect whether its iteration can be done multiple times, which can be used
     *  to carefully buffer/collect single use streams when repeated iterations are required.
     */
    fun supportsReuse(): Boolean

}
