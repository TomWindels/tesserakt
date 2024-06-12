package dev.tesserakt.sparql.runtime.incremental.state

import dev.tesserakt.sparql.runtime.core.Mapping
import dev.tesserakt.sparql.runtime.util.Bitmask
import dev.tesserakt.util.compatibleWith

/**
 * Adds all results found inside `this` list together where compatible as additional contenders for complete result
 *  generation (for input quads matching multiple patterns at once)
 */
internal inline fun List<Pair<Bitmask, List<Mapping>>>.expandResultSet(): List<Pair<Bitmask, List<Mapping>>> {
    // estimating about half of them match for initial capacity
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
            }
        }
        ++i
    }
    return result
}
