package dev.tesserakt.sparql.runtime.incremental.stream

internal interface Stream<out E: Any>: Iterable<E> {

    /**
     * The cardinality of this stream: a guaranteed upper bound (= worst case) number of mappings that can
     *  be iterated over
     */
    val cardinality: Int

    fun isEmpty(): Boolean

    /**
     * Analyses this stream's dependencies to detect whether its iteration can be done efficiently, which can be used
     *  to carefully buffer/collect inefficient streams when repeated iterations are planned.
     */
    fun supportsEfficientIteration(): Boolean

}
