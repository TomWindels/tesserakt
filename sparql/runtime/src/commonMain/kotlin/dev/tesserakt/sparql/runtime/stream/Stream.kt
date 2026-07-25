package dev.tesserakt.sparql.runtime.stream

import dev.tesserakt.sparql.util.Cardinality

/**
 * The base Stream type, used to do lazy iteration by chaining operations together, such as mapping the output of an
 *  iterator to a new (set of) iterator(s) without requiring intermediate collections.
 *
 * Note that this uses a form of value semantics: using an operator on a stream (e.g. [Stream.chain]) may affect `this`
 *  stream instance. Because of this, applying an operator on a stream means that the original stream should be
 *  considered invalid for other use!
 */
interface Stream<out E: Any>: Iterable<E> {

    /**
     * The cardinality of this stream: a guaranteed upper bound (= worst case) number of mappings that can
     *  be iterated over
     */
    val cardinality: Cardinality

    /**
     * Serves as a quick `isEmpty` check. If it has zero cardinality, no elements are guaranteed to be returned, and the
     *  stream can be replaced with an empty one.
     *
     * IMPORTANT: the opposite does NOT hold, as it is not identical to an `isEmpty` check! Having a non-zero cardinality
     *  does not mean elements are guaranteed!
     */
    fun hasZeroCardinality(): Boolean

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
