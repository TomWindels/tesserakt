package dev.tesserakt.sparql.runtime.incremental.stream

import dev.tesserakt.sparql.runtime.core.Mapping
import dev.tesserakt.sparql.runtime.incremental.collection.MappingArray
import dev.tesserakt.sparql.runtime.incremental.delta.MappingDelta
import dev.tesserakt.sparql.runtime.incremental.state.MutableJoinState

/* simple extensions, aliases of the various builders */

internal fun Stream<Mapping>.join(other: Stream<Mapping>): Stream<Mapping> =
    if (other.isEmpty() || this.isEmpty()) emptyStream() else StreamMultiJoin(this, other)

internal fun Stream<Mapping>.join(other: Mapping): Stream<Mapping> =
    if (isEmpty()) emptyStream() else StreamSingleJoin(other, this)

internal fun Mapping.join(other: Stream<Mapping>): Stream<Mapping> =
    if (other.isEmpty()) emptyStream() else StreamSingleJoin(this, other)

internal fun <E: Any> Stream<E>.remove(elements: Iterable<E>): Stream<E> =
    StreamReduction(this, removed = elements)

internal fun <E: Any> Stream<E>.chain(other: Stream<E>): Stream<E> = when {
    isEmpty() && other.isEmpty() -> emptyStream()
    isEmpty() -> other
    other.isEmpty() -> this
    else -> StreamChain(this, other)
}

// it's more common to use product's between same-type streams than it is to use different-type streams, whilst
//  same-type streams allow for the optimisation described in the implementation comments; in different-type stream
//  product cases, a ProductStream has to be created manually, avoiding overload ambiguity
internal fun <E: Any> Stream<E>.product(other: Stream<E>): Stream<Pair<E, E>> = when {
    isEmpty() || other.isEmpty() -> emptyStream()
    // using the smallest cardinality on the right (b), as that one's repeated as long as the left
    //  stream can produce; larger cardinality streams have a higher likelihood of actually skipping a lot of
    //  combinations (i.e. produced by a join), where unnecessary rechecks (through repeated iterations)
    //  should be avoided
    cardinality < other.cardinality -> {
        StreamProduct(other, this)
    }
    else -> {
        StreamProduct(this, other)
    }
}

internal fun <E: Any> Iterable<Stream<E>>.merge(): Stream<E> {
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

internal inline fun <I: Any, O: Any> Iterable<Stream<I>>.transform(transform: (Stream<I>) -> Stream<O>): Stream<O> {
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

internal inline fun <I: Any, O: Any> Iterable<Stream<I>>.transform(transform: (Int, Stream<I>) -> Stream<O>): Stream<O> {
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

internal inline fun <I: Any, O: Any> OptimisedStream<I>.transform(noinline transform: (I) -> Stream<O>): Stream<O> {
    return if (isEmpty()) emptyStream() else StreamTransform(source = this, transform = transform)
}

internal inline fun <I: Any, O: Any> OptimisedStream<I>.transformNonNull(noinline transform: (I) -> Stream<O>?): Stream<O> {
    return if (isEmpty()) emptyStream() else StreamTransformNullable(source = this, transform = transform)
}

internal inline fun <I: Any, O: Any> Stream<I>.mapped(noinline transform: (I) -> O): Stream<O> {
    return if (isEmpty()) emptyStream() else StreamMapping(source = this, transform = transform)
}

internal inline fun <I: Any, O: Any> Stream<I>.mappedNonNull(noinline transform: (I) -> O?): Stream<O> {
    return if (isEmpty()) emptyStream() else StreamMappingNullable(source = this, transform = transform)
}

internal fun <E: Any> Stream<E>.collect(): CollectedStream<E> =
    if (this is CollectedStream) this else CollectedStream(this)

// TODO(perf): chain optimisation: only optimise subsets of unoptimised chain elements
internal fun <E: Any> Stream<E>.optimised(): OptimisedStream<E> = when {
    isEmpty() -> emptyStream()
    this is OptimisedStream -> this
    supportsEfficientIteration() -> OptimisedStreamView(this)
    // TODO: check for memory preference: buffering or wrapping depending on whether array use is allowed
    else -> BufferedStream(this)
}

/* typical usage pattern chains */

internal fun MappingArray.join(other: Mapping): Stream<Mapping> = iter(other).join(other)

internal fun MappingArray.join(other: OptimisedStream<Mapping>): Stream<Mapping> =
    other.transform { mapping -> join(mapping) }

internal fun MutableJoinState.join(other: OptimisedStream<MappingDelta>): Stream<MappingDelta> =
    other.transform { this.join(it) }
