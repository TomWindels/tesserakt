package dev.tesserakt.sparql.runtime.query

import dev.tesserakt.sparql.newAnonymousBinding
import dev.tesserakt.sparql.runtime.evaluation.MappingDelta
import dev.tesserakt.sparql.runtime.evaluation.plus
import dev.tesserakt.sparql.runtime.query.jointree.JoinTree
import dev.tesserakt.sparql.runtime.stream.OptimisedStream
import dev.tesserakt.sparql.runtime.stream.Stream
import dev.tesserakt.sparql.runtime.stream.mappedNonNull
import dev.tesserakt.sparql.runtime.stream.product
import dev.tesserakt.sparql.types.TriplePattern
import dev.tesserakt.sparql.types.bindingName
import dev.tesserakt.sparql.util.Bitmask

/**
 * Adds all results found inside `this` list together where compatible as additional contenders for complete result
 *  generation (for input quads matching multiple patterns at once)
 */
inline fun List<Pair<Bitmask, List<MappingDelta>>>.expandBindingDeltas(): List<Pair<Bitmask, List<MappingDelta>>> {
    val result = toMutableList()
    var i = 0
    while (i < result.size - 1) {
        val current = result[i]
        (i + 1 until result.size).forEach { j ->
            val contender = result[j]
            if (!current.first.and(contender.first).isZero()) {
                // pattern (partially) already applied, no merging should be done
                return@forEach
            }
            // creating all mappings that result from combining these two sub-results
            val merged = joinLists(current.second, contender.second)
            // if any have been made, its combination can be appended to this result
            if (merged.isNotEmpty()) {
                result.add(current.first or contender.first to merged)
            }
        }
        ++i
    }
    // TODO(perf): simplify the result: [+ {a}, + {b}, - {a}] == [+ {b}]
    return result
}

fun joinLists(a: List<MappingDelta>, b: List<MappingDelta>): List<MappingDelta> =
    buildList(a.size + b.size) {
        a.forEach { one -> b.forEach { two -> (one + two)?.let { merged -> add(merged) } } }
    }

fun join(a: Stream<MappingDelta>, b: OptimisedStream<MappingDelta>): Stream<MappingDelta> =
    a.product(b).mappedNonNull { (a, b) -> a + b }

fun join(a: OptimisedStream<MappingDelta>, b: Stream<MappingDelta>): Stream<MappingDelta> =
    a.product(b).mappedNonNull { (a, b) -> a + b }

fun join(a: OptimisedStream<MappingDelta>, b: OptimisedStream<MappingDelta>): Stream<MappingDelta> =
    a.product(b).mappedNonNull { (a, b) -> a + b }

inline fun bindingNamesOf(
    subject: TriplePattern.Subject,
    predicate: TriplePattern.Predicate,
    `object`: TriplePattern.Object
): Set<String> = setOfNotNull(subject.bindingName, predicate.bindingName, `object`.bindingName)

inline fun JoinTree.join(deltas: List<MappingDelta>): List<MappingDelta> {
    return deltas.flatMap { delta -> join(delta) }
}

inline fun TriplePattern.UnboundSequence.unfold(start: TriplePattern.Subject, end: TriplePattern.Object): List<TriplePattern> {
    require(chain.size >= 2)
    val result = ArrayList<TriplePattern>(chain.size)
    var subj = start
    (0 until chain.size - 1).forEach { i ->
        val p = chain[i]
        val obj = newAnonymousBinding()
        result.add(TriplePattern(subj, p, obj))
        subj = obj.asSubject()
    }
    result.add(TriplePattern(subj, chain.last(), end))
    return result
}

inline fun TriplePattern.Sequence.unfold(start: TriplePattern.Subject, end: TriplePattern.Object): List<TriplePattern> {
    require(chain.size >= 2)
    val result = ArrayList<TriplePattern>(chain.size)
    var subj = start
    (0 until chain.size - 1).forEach { i ->
        val p = chain[i]
        val obj = newAnonymousBinding()
        result.add(TriplePattern(subj, p, obj))
        subj = obj.asSubject()
    }
    result.add(TriplePattern(subj, chain.last(), end))
    return result
}

fun TriplePattern.Object.asSubject(): TriplePattern.Subject = when (this) {
    is TriplePattern.GeneratedBinding -> this
    is TriplePattern.NamedBinding -> this
    is TriplePattern.Exact -> this
}
