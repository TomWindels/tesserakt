package dev.tesserakt.sparql.runtime.stream

import dev.tesserakt.sparql.runtime.collection.MappingArray
import dev.tesserakt.sparql.runtime.evaluation.Mapping
import dev.tesserakt.sparql.runtime.evaluation.MappingDelta
import dev.tesserakt.sparql.runtime.query.MutableJoinState
import dev.tesserakt.sparql.util.Cardinality
import dev.tesserakt.sparql.util.ZeroCardinality

/* simple extensions, aliases of the various builders */

/**
 * Serves as a quick `isEmpty` check. If it has zero cardinality, no elements are guaranteed to be returned, and the
 *  stream can be replaced with an empty one.
 *
 * IMPORTANT: the opposite does NOT hold, as it is not identical to an `isEmpty` check! Having a non-zero cardinality
 *  does not mean elements are guaranteed!
 */
fun <E : Any> Stream<E>.hasZeroCardinality(): Boolean = cardinality == ZeroCardinality

fun OptimisedStream<Mapping>.join(other: Stream<Mapping>): Stream<Mapping> =
    if (other.hasZeroCardinality() || this.hasZeroCardinality()) emptyStream() else StreamMultiJoin(
        other,
        this
    )

fun Stream<Mapping>.join(other: OptimisedStream<Mapping>): Stream<Mapping> =
    if (other.hasZeroCardinality() || this.hasZeroCardinality()) emptyStream() else StreamMultiJoin(
        this,
        other
    )

fun OptimisedStream<Mapping>.join(other: OptimisedStream<Mapping>): Stream<Mapping> =
    if (other.hasZeroCardinality() || this.hasZeroCardinality()) emptyStream() else StreamMultiJoin(
        this,
        other
    )

fun Stream<Mapping>.join(other: Mapping): Stream<Mapping> =
    if (hasZeroCardinality()) emptyStream() else StreamSingleJoin(other, this)

fun Mapping.join(other: Stream<Mapping>): Stream<Mapping> =
    if (other.hasZeroCardinality()) emptyStream() else StreamSingleJoin(this, other)

fun <E : Any> Stream<E>.remove(elements: Iterable<E>): Stream<E> =
    StreamReduction(this, removed = elements)

fun <E : Any> Stream<E>.chain(other: Stream<E>): Stream<E> = when {
    hasZeroCardinality() && other.hasZeroCardinality() -> emptyStream()
    hasZeroCardinality() -> other
    other.hasZeroCardinality() -> this
    else -> StreamChain(this, other)
}

fun <E : Any> OptimisedStream<E>.chain(other: OptimisedStream<E>): OptimisedStream<E> = when {
    hasZeroCardinality() && other.hasZeroCardinality() -> emptyStream()
    hasZeroCardinality() -> other
    other.hasZeroCardinality() -> this
    else -> OptimisedStreamView(StreamChain(this, other))
}

// it's more common to use product's between same-type streams than it is to use different-type streams, whilst
//  same-type streams allow for the optimisation described in the implementation comments; in different-type stream
//  product cases, a ProductStream has to be created manually, avoiding overload ambiguity
fun <E : Any> OptimisedStream<E>.product(other: Stream<E>): Stream<Pair<E, E>> = when {
    hasZeroCardinality() || other.hasZeroCardinality() -> emptyStream()
    else -> {
        StreamProduct(other, this)
    }
}

fun <E : Any> Stream<E>.product(other: OptimisedStream<E>): Stream<Pair<E, E>> = when {
    hasZeroCardinality() || other.hasZeroCardinality() -> emptyStream()
    else -> {
        StreamProduct(this, other)
    }
}

fun <E : Any> OptimisedStream<E>.product(other: OptimisedStream<E>): OptimisedStream<Pair<E, E>> = when {
    hasZeroCardinality() || other.hasZeroCardinality() -> emptyStream()
    // using the smallest cardinality on the right (b), as that one's repeated as long as the left
    //  stream can produce; larger cardinality streams have a higher likelihood of actually skipping a lot of
    //  combinations (i.e. produced by a join), where unnecessary rechecks (through repeated iterations)
    //  should be avoided
    cardinality < other.cardinality -> {
        OptimisedStreamView(StreamProduct(other, this))
    }

    else -> {
        OptimisedStreamView(StreamProduct(this, other))
    }
}

fun <E : Any> Iterable<Stream<E>>.merge(): Stream<E> {
    val iter = iterator()
    if (!iter.hasNext()) {
        return emptyStream()
    }
    var result = iter.next()
    while (iter.hasNext()) {
        result = result.chain(iter.next())
    }
    return result
}

inline fun <I : Any, O : Any> Stream<I>.merge(transform: (I) -> Stream<O>): Stream<O> {
    val iter = iterator()
    if (!iter.hasNext()) {
        return emptyStream()
    }
    var result = transform(iter.next())
    while (iter.hasNext()) {
        result = result.chain(transform(iter.next()))
    }
    return result
}

inline fun <I : Any, O : Any> Stream<I>.folded(start: Stream<O>, transform: (acc: Stream<O>, element: I) -> Stream<O>): Stream<O> {
    val iter = iterator()
    if (!iter.hasNext()) {
        return start
    }
    var result = transform(start, iter.next())
    while (iter.hasNext()) {
        result = transform(result, iter.next())
    }
    return result
}

inline fun <I : Any> Stream<I>.zippedWithIndex(): Stream<Pair<Int, I>> {
    return StreamWithIndex(parent = this)
}

inline fun <I : Any, O : Any> Iterable<Stream<I>>.transform(transform: (Stream<I>) -> Stream<O>): Stream<O> {
    val iter = iterator()
    if (!iter.hasNext()) {
        return emptyStream()
    }
    var result = transform(iter.next())
    while (iter.hasNext()) {
        result = result.chain(transform(iter.next()))
    }
    return result
}

inline fun <I : Any, O : Any> Iterable<Stream<I>>.transform(transform: (Int, Stream<I>) -> Stream<O>): Stream<O> {
    val iter = iterator()
    if (!iter.hasNext()) {
        return emptyStream()
    }
    var i = 0
    var result = transform(i, iter.next())
    while (iter.hasNext()) {
        ++i
        result = result.chain(transform(i, iter.next()))
    }
    return result
}

/**
 * Transforms this [OptimisedStream] using the provided [transform]. The [maxCardinality] value represents the max
 *  expected cardinality of the [Stream] returned by the [transform] invocation.
 */
inline fun <I : Any, O : Any> OptimisedStream<I>.transform(
    /** The largest cardinality value possible for streams obtained through [transform] **/
    maxCardinality: Cardinality,
    noinline transform: (I) -> Stream<O>,
): Stream<O> {
    return if (hasZeroCardinality()) emptyStream() else StreamTransform(
        source = this,
        transform = transform,
        cardinality = cardinality * maxCardinality
    )
}

/**
 * Transforms this [OptimisedStream] using the provided [transform]. The [maxCardinality] value represents the max
 *  expected cardinality of the [Stream] returned by the [transform] invocation.
 */
inline fun <I : Any, O : Any> OptimisedStream<I>.transform(
    /** The largest cardinality value possible for streams obtained through [transform] **/
    maxCardinality: Number,
    noinline transform: (I) -> Stream<O>,
): Stream<O> {
    return transform(maxCardinality = Cardinality(maxCardinality), transform)
}

/**
 * Transforms this [OptimisedStream] using the provided [transform]. The [maxCardinality] value represents the max
 *  expected cardinality of the non-null [Stream]s returned by the [transform] invocation.
 */
inline fun <I : Any, O : Any> OptimisedStream<I>.transformNonNull(
    /** The largest cardinality value possible for streams obtained through [transform] **/
    maxCardinality: Cardinality,
    noinline transform: (I) -> Stream<O>?
): Stream<O> {
    return if (hasZeroCardinality()) emptyStream() else StreamTransformNullable(
        source = this,
        transform = transform,
        cardinality = cardinality * maxCardinality
    )
}

/**
 * Transforms this [OptimisedStream] using the provided [transform]. The [maxCardinality] value represents the max
 *  expected cardinality of the non-null [Stream]s returned by the [transform] invocation.
 */
inline fun <I : Any, O : Any> OptimisedStream<I>.transformNonNull(
    /** The largest cardinality value possible for streams obtained through [transform] **/
    maxCardinality: Number,
    noinline transform: (I) -> Stream<O>?
): Stream<O> {
    return transformNonNull(maxCardinality = Cardinality(maxCardinality), transform = transform)
}

inline fun <I : Any, O : Any> Stream<I>.mapped(noinline transform: (I) -> O): Stream<O> {
    return if (hasZeroCardinality()) emptyStream() else StreamMapping(
        source = this,
        transform = transform
    )
}

inline fun <I : Any, O : Any> OptimisedStream<I>.mapped(noinline transform: (I) -> O): OptimisedStream<O> {
    return if (hasZeroCardinality()) emptyStream() else OptimisedStreamView(
        StreamMapping(
            source = this,
            transform = transform
        )
    )
}

inline fun <I : Any, O : Any> Stream<I>.mappedNonNull(noinline transform: (I) -> O?): Stream<O> {
    return if (hasZeroCardinality()) emptyStream() else StreamMappingNullable(
        source = this,
        transform = transform
    )
}

inline fun <reified O : Any> Stream<*>.filteredIsInstance(): Stream<O> {
    return mappedNonNull { it as? O }
}

inline fun <E : Any> Stream<E>.filtered(noinline predicate: (E) -> Boolean): Stream<E> {
    return if (hasZeroCardinality()) emptyStream() else StreamFilter(
        source = this,
        predicate = predicate
    )
}

inline fun <E : Any> Stream<E>.collect(): CollectedStream<E> =
    if (this is CollectedStream) this else CollectedStream(this)

/**
 * Caches this stream into the target [stream], appending elements
 */
inline fun <E : Any> Stream<E>.collectTo(stream: CachedStream<E>): OptimisedStream<E> {
    stream.insert(this)
    return stream
}

fun <E : Any> Stream<E>.optimisedForReuse(): OptimisedStream<E> = when {
    hasZeroCardinality() -> emptyStream()
    // we *have* to buffer these, as otherwise reuse is not possible
    !supportsReuse() -> BufferedStream(this)
    this is OptimisedStream -> this
    supportsEfficientIteration() -> OptimisedStreamView(this)
    this is StreamChain<E> -> {
        val s1 = if (!source1.supportsEfficientIteration()) {
            source1.optimisedForReuse()
        } else {
            OptimisedStreamView(source1)
        }
        val s2 = if (!source2.supportsEfficientIteration()) {
            source2.optimisedForReuse()
        } else {
            OptimisedStreamView(source2)
        }
        // as the chain is now made up of optimised streams, the iteration chain are now also automatically optimised
        // so it can be wrapped without consequences
        OptimisedStreamView(StreamChain(s1, s2))
    }
    // TODO: check for memory preference: buffering or wrapping depending on whether array use is allowed
    else -> BufferedStream(this)
}

inline fun <E : Any> Stream<E>.optimisedForSingleUse(cardinality: Cardinality = this.cardinality): OptimisedStream<E> =
    when {
        hasZeroCardinality() -> emptyStream()
        this is OptimisedStream -> this
        supportsEfficientIteration() -> OptimisedStreamView(this)
        // instead of buffering the thing, we're only exposing the iterator once
        else -> SingleUseStreamView(this, cardinality)
    }

inline fun <E : Any> Stream<E>.optimisedForSingleUse(cardinality: Number): OptimisedStream<E> =
    optimisedForSingleUse(cardinality = Cardinality(cardinality))

/* typical usage pattern chains */

fun MappingArray.join(other: Mapping): Stream<Mapping> = iter(other).join(other)

fun MappingArray.join(other: OptimisedStream<Mapping>): Stream<Mapping> =
    other.transform(maxCardinality = other.cardinality) { mapping -> join(mapping) }

fun MutableJoinState.join(other: OptimisedStream<MappingDelta>): Stream<MappingDelta> =
    other.transform(maxCardinality = other.cardinality) { this.join(it) }
