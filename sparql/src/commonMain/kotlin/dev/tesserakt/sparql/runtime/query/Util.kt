package dev.tesserakt.sparql.runtime.query

import dev.tesserakt.sparql.ast.TriplePattern
import dev.tesserakt.sparql.ast.bindingName
import dev.tesserakt.sparql.runtime.evaluation.MappingDelta
import dev.tesserakt.sparql.runtime.evaluation.plus
import dev.tesserakt.sparql.runtime.stream.OptimisedStream
import dev.tesserakt.sparql.runtime.stream.Stream
import dev.tesserakt.sparql.runtime.stream.mappedNonNull
import dev.tesserakt.sparql.runtime.stream.product
import dev.tesserakt.sparql.util.Bitmask

/**
 * Adds all results found inside `this` list together where compatible as additional contenders for complete result
 *  generation (for input quads matching multiple patterns at once)
 */
internal inline fun List<Pair<Bitmask, List<MappingDelta>>>.expandBindingDeltas(): List<Pair<Bitmask, List<MappingDelta>>> {
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
            val merged = join(current.second, contender.second)
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

internal fun join(a: List<MappingDelta>, b: List<MappingDelta>): List<MappingDelta> =
    buildList(a.size + b.size) {
        a.forEach { one -> b.forEach { two -> (one + two)?.let { merged -> add(merged) } } }
    }

internal fun join(a: Stream<MappingDelta>, b: OptimisedStream<MappingDelta>): Stream<MappingDelta> =
    a.product(b).mappedNonNull { (a, b) -> a + b }

internal fun join(a: OptimisedStream<MappingDelta>, b: Stream<MappingDelta>): Stream<MappingDelta> =
    a.product(b).mappedNonNull { (a, b) -> a + b }

internal fun join(a: OptimisedStream<MappingDelta>, b: OptimisedStream<MappingDelta>): Stream<MappingDelta> =
    a.product(b).mappedNonNull { (a, b) -> a + b }

internal inline fun bindingNamesOf(
    subject: TriplePattern.Subject,
    predicate: TriplePattern.Predicate,
    `object`: TriplePattern.Object
): Set<String> = setOfNotNull(subject.bindingName, predicate.bindingName, `object`.bindingName)

internal inline fun JoinTree.join(deltas: List<MappingDelta>): List<MappingDelta> {
    return deltas.flatMap { delta -> join(delta) }
}

internal inline fun TriplePattern.UnboundSequence.unfold(start: TriplePattern.Subject, end: TriplePattern.Object): List<TriplePattern> {
    require(chain.size >= 2)
    val result = ArrayList<TriplePattern>(chain.size)
    var subj = start
    (0 until chain.size - 1).forEach { i ->
        val p = chain[i]
        val obj = createAnonymousBinding()
        result.add(TriplePattern(subj, p, obj))
        subj = obj.toSubject()
    }
    result.add(TriplePattern(subj, chain.last(), end))
    return result
}

internal inline fun TriplePattern.Sequence.unfold(start: TriplePattern.Subject, end: TriplePattern.Object): List<TriplePattern> {
    require(chain.size >= 2)
    val result = ArrayList<TriplePattern>(chain.size)
    var subj = start
    (0 until chain.size - 1).forEach { i ->
        val p = chain[i]
        val obj = createAnonymousBinding()
        result.add(TriplePattern(subj, p, obj))
        subj = obj.toSubject()
    }
    result.add(TriplePattern(subj, chain.last(), end))
    return result
}

private fun TriplePattern.Object.toSubject(): TriplePattern.Subject = when (this) {
    is TriplePattern.GeneratedBinding -> this
    is TriplePattern.NamedBinding -> this
    is TriplePattern.Exact -> this
}

private var generatedBindingIndex = 0

internal fun createAnonymousBinding() = TriplePattern.GeneratedBinding(id = generatedBindingIndex++)
