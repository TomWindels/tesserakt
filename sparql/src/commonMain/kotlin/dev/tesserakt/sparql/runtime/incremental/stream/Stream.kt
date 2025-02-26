package dev.tesserakt.sparql.runtime.incremental.stream

internal interface Stream<out E: Any>: Iterable<E> {

    /**
     * The cardinality of this stream: a guaranteed upper bound (= worst case) number of mappings that can
     *  be iterated over
     */
    val cardinality: Int

    fun isEmpty(): Boolean

}
