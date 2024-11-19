package dev.tesserakt.sparql.runtime.incremental.state

import dev.tesserakt.sparql.runtime.common.types.Pattern
import dev.tesserakt.sparql.runtime.core.Mapping
import dev.tesserakt.sparql.runtime.core.pattern.bindingName
import dev.tesserakt.sparql.runtime.util.Bitmask
import dev.tesserakt.util.compatibleWith

/**
 * Merges the results found in the receiving collection to a new set of candidate results that guarantee no overlap
 */
internal inline fun List<Pair<Bitmask, List<Mapping>>>.merge(): List<Pair<Bitmask, List<Mapping>>> {
    // estimating about half of them match for initial capacity
    val result = toMutableList()
    var i = 0
    while (i < result.size - 1) {
        val current = result[i]
        var j = i + 1
        while (j < result.size) {
            val contender = result[j]
            // cannot be already partially applied
            if (!current.first.and(contender.first).isZero()) {
                ++j
                continue
            }
            // creating all mappings that result from combining these two sub-results
            val merged = current.second.flatMap { mapping ->
                contender.second.mapNotNull { candidate ->
                    if (mapping.compatibleWith(candidate)) {
                        mapping + candidate
                    } else null
                }
            }
            // if any have been made, its combination can be appended to this result
            if (merged.isNotEmpty()) {
                result.add(current.first or contender.first to merged)
                result.removeAt(j)
                result.removeAt(i)
            } else {
                ++j
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
