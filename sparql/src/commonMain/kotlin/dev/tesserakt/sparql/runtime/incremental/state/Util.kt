package dev.tesserakt.sparql.runtime.incremental.state

import dev.tesserakt.sparql.runtime.common.types.Pattern
import dev.tesserakt.sparql.runtime.core.pattern.bindingName
import dev.tesserakt.sparql.runtime.incremental.delta.MappingDelta
import dev.tesserakt.sparql.runtime.incremental.delta.plus
import dev.tesserakt.sparql.runtime.incremental.stream.Stream
import dev.tesserakt.sparql.runtime.incremental.stream.mappedNonNull
import dev.tesserakt.sparql.runtime.incremental.stream.product
import dev.tesserakt.sparql.runtime.util.Bitmask

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

internal fun join(a: Stream<MappingDelta>, b: Stream<MappingDelta>): Stream<MappingDelta> =
    a.product(b).mappedNonNull { (a, b) -> a + b }

internal inline fun bindingNamesOf(
    subject: Pattern.Subject,
    predicate: Pattern.Predicate,
    `object`: Pattern.Object
): Set<String> = setOfNotNull(subject.bindingName, predicate.bindingName, `object`.bindingName)

internal inline fun JoinTree.join(deltas: List<MappingDelta>): List<MappingDelta> {
    return deltas.flatMap { delta -> join(delta) }
}

internal inline fun Pattern.UnboundSequence.unfold(start: Pattern.Subject, end: Pattern.Object): List<Pattern> {
    require(chain.size >= 2)
    val result = ArrayList<Pattern>(chain.size)
    var subj = start
    (0 until chain.size - 1).forEach { i ->
        val p = chain[i]
        val obj = createAnonymousBinding()
        result.add(Pattern(subj, p, obj))
        subj = obj.toSubject()
    }
    result.add(Pattern(subj, chain.last(), end))
    return result
}

internal inline fun Pattern.Sequence.unfold(start: Pattern.Subject, end: Pattern.Object): List<Pattern> {
    require(chain.size >= 2)
    val result = ArrayList<Pattern>(chain.size)
    var subj = start
    (0 until chain.size - 1).forEach { i ->
        val p = chain[i]
        val obj = createAnonymousBinding()
        result.add(Pattern(subj, p, obj))
        subj = obj.toSubject()
    }
    result.add(Pattern(subj, chain.last(), end))
    return result
}

private fun Pattern.Object.toSubject(): Pattern.Subject = when (this) {
    is Pattern.GeneratedBinding -> this
    is Pattern.RegularBinding -> this
    is Pattern.Exact -> this
}

private var generatedBindingIndex = 0

internal fun createAnonymousBinding() = Pattern.GeneratedBinding(id = generatedBindingIndex++)
