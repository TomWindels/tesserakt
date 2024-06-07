package dev.tesserakt.sparql.runtime.incremental.state

import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.sparql.runtime.core.Mapping
import dev.tesserakt.sparql.runtime.core.pattern.TriplePattern
import dev.tesserakt.sparql.runtime.incremental.pattern.IncrementalBasicGraphPattern
import dev.tesserakt.sparql.runtime.util.Bitmask
import dev.tesserakt.util.compatibleWith

internal class IncrementalBasicGraphPatternState(parent: IncrementalBasicGraphPattern) {

    private val patterns = parent.patterns.map { it.createIncrementalPatternState() }

    fun process(quad: Quad): List<Mapping> {
        return patterns
            .mapIndexed { i, pattern -> Bitmask.onesAt(i, length = patterns.size) to pattern.delta(quad) }
            .expanded()
            .flatMap { (mask, mappings) ->
                // as we only need to iterate over the patterns not yet managed, we need to inverse the bitmask
                //  before iterating over it
                mask.inv().fold(mappings) { results, i -> patterns[i].join(results) }
            }
            .also {
                // inserting this input to all patterns
                patterns.forEach { it.insert(quad) }
            }
    }

}

private fun TriplePattern.createIncrementalPatternState(): IncrementalTriplePatternState = when (this) {
    is TriplePattern.NonRepeating -> IncrementalTriplePatternState.NonRepeating.ArrayBacked(pattern = this)
    is TriplePattern.Repeating -> IncrementalTriplePatternState.Repeating(pattern = this)
}

/**
 * Adds all results found inside `this` list together where compatible as additional contenders for complete result
 *  generation (for input quads matching multiple patterns at once)
 */
private inline fun List<Pair<Bitmask, List<Mapping>>>.expanded(): List<Pair<Bitmask, List<Mapping>>> {
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
