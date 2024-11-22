package dev.tesserakt.sparql.runtime.incremental.state

import dev.tesserakt.sparql.runtime.common.types.Pattern
import dev.tesserakt.sparql.runtime.core.pattern.bindingName
import dev.tesserakt.sparql.runtime.incremental.delta.Delta
import dev.tesserakt.sparql.runtime.incremental.delta.plus
import dev.tesserakt.sparql.runtime.util.Bitmask

/**
 * Adds all results found inside `this` list together where compatible as additional contenders for complete result
 *  generation (for input quads matching multiple patterns at once)
 */
internal inline fun List<Pair<Bitmask, List<Delta.Bindings>>>.expandBindingDeltas(): List<Pair<Bitmask, List<Delta.Bindings>>> {
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
            val merged = current.second.flatMap { solution ->
                contender.second.mapNotNull { candidate -> solution + candidate }
            }
            // if any have been made, its combination can be appended to this result
            if (merged.isNotEmpty()) {
                result.add(current.first or contender.first to merged)
            }
        }
        ++i
    }
    return result
}

internal inline fun bindingNamesOf(
    subject: Pattern.Subject,
    predicate: Pattern.Predicate,
    `object`: Pattern.Object
): Set<String> = setOfNotNull(subject.bindingName, predicate.bindingName, `object`.bindingName)

internal inline fun JoinTree.join(deltas: List<Delta.Bindings>): List<Delta.Bindings> {
    return deltas.flatMap { delta -> join(delta) }
}
