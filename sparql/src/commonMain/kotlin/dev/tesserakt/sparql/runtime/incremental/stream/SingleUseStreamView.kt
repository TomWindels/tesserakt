package dev.tesserakt.sparql.runtime.incremental.stream

import dev.tesserakt.sparql.runtime.incremental.types.Cardinality

/**
 * Similar to a [OptimisedStreamView], in the sense that it exposes the underlying iterator directly, whilst marking
 *  it as optimised, but with an additional runtime check to ensure it's only consumed once, as the use case for this
 *  wrapper (as opposed to the [OptimisedStreamView] variant) is skipping buffering elements that won't be reused,
 *  saving no processing time
 */
internal class SingleUseStreamView<E: Any> private constructor(
    private var source: Iterator<E>?,
    private val sourceDescription: String,
    override val cardinality: Cardinality,
): OptimisedStream<E> {

    constructor(
        source: Stream<E>,
        cardinality: Cardinality
    ): this(
        source = source.iterator(),
        sourceDescription = source.description,
        cardinality = cardinality
    )

    override val description: String
        get() = "SingleUseStream[${sourceDescription}; consumed=${source == null}]"

    override fun iterator(): Iterator<E> {
        val iter = source
        source = null
        return iter ?: throw NoSuchElementException("$sourceDescription has already been consumed!")
    }

}
