package dev.tesserakt.sparql.runtime.incremental.iterable

import dev.tesserakt.sparql.runtime.core.Mapping
import dev.tesserakt.sparql.runtime.incremental.collection.MappingArray

/* general utilities */
internal fun Iterable<Mapping>.isEmpty() = !iterator().hasNext()

/* simple extensions, aliases of the various builders */

internal fun Iterable<Mapping>.join(other: Iterable<Mapping>): Iterable<Mapping> =
    MultiJoinIterable(this, other)

internal fun Iterable<Mapping>.join(other: Mapping): Iterable<Mapping> =
    SingleJoinIterable(other, this)

internal fun Mapping.join(other: Iterable<Mapping>): Iterable<Mapping> =
    SingleJoinIterable(this, other)

internal fun Iterable<Mapping>.remove(elements: Iterable<Mapping>): Iterable<Mapping> =
    RemoveIterable(this, removed = elements)

internal fun Iterable<Mapping>.collect(): List<Mapping> = toList()

/* typical usage pattern chains */

internal fun MappingArray.join(other: Mapping): Iterable<Mapping> = iter(other).join(other)

internal fun MappingArray.join(other: List<Mapping>): Iterable<Mapping> =
    // TODO (perf): no need to collect ("flatmap") prematurely here, maybe create a new indexed join type?
    iter(other).flatMapIndexed { i, iter -> iter.join(other[i]) }
