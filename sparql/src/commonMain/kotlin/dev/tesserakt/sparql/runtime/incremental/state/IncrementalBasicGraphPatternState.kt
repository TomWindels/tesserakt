package dev.tesserakt.sparql.runtime.incremental.state

import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.sparql.runtime.core.Mapping
import dev.tesserakt.sparql.runtime.incremental.state.IncrementalTriplePatternState.Companion.createIncrementalPatternState
import dev.tesserakt.sparql.runtime.incremental.types.Query
import dev.tesserakt.sparql.runtime.util.Bitmask

internal class IncrementalBasicGraphPatternState(ast: Query.QueryBody) {

    private val patternCache = MappingCache.SingleChainLeft()
    private val unionCache = MappingCache.SingleChainLeft()
    private val patterns = ast.patterns.map { it.createIncrementalPatternState() }
    private val unions = ast.unions.map { union -> IncrementalUnionState(union) }

    fun delta(quad: Quad): List<Mapping> {
        // TODO:
        //  * run the pattern delta for the new quad
        //  * expand the result set
        //  * get all cached pattern states
        //  * grow the cached results with the individual pattern deltas based on compatible masks
        //    (i.e. delta 0b100 is compatible with cache results from 0b001, 0b010 and 0b011, where present)
        //  * grow all other not-part-of-cache results that can still create complete results
        //    (i.e. delta 0b010 can still yield results if mask 0b101 is not part of cached states)
        val base = patterns
            .mapIndexed { i, pattern -> Bitmask.onesAt(i, length = patterns.size) to pattern.delta(quad) }
            .expandResultSet()
            .flatMap { (mask, mappings) ->
                // as we only need to iterate over the patterns not yet managed, we need to inverse the bitmask
                //  before iterating over it
                mask.inv().fold(mappings) { results, i -> patterns[i].join(results) }
            }
        val first = unions.fold(initial = base) { results, u -> u.join(results) }
        return first + unions
            .mapIndexed { i, union -> Bitmask.onesAt(i, length = unions.size) to union.delta(quad) }
            .expandResultSet()
            .flatMap { (mask, mappings) ->
                // as we only need to iterate over the unions not yet managed, we need to inverse the bitmask
                //  before iterating over it
                mask.inv().fold(mappings) { results, i -> unions[i].join(results) }
            }
            .let { patterns.fold(it) { results, p -> p.join(results) } }
    }

    fun process(quad: Quad): List<Mapping> {
        val results = delta(quad)
        insert(quad)
        return results
    }

    fun insert(quad: Quad) {
        // first updating the cache before individual child states are altered so the delta calculations are correct
        updateCache(quad)
        // now the child states can also insert the quad
        patterns.forEach { it.insert(quad) }
        unions.forEach { it.insert(quad) }
    }

    fun join(mappings: List<Mapping>): List<Mapping> = unions
        .fold(initial = patterns.fold(mappings) { results, p -> p.join(results) }) { results, u -> u.join(results) }

    // TODO: overhaul this state's API so delta + insertion can be done in a single call, which can then also
    //  reduce the # of "delta processing" required
    // TODO: may also benefit from the same original cache use as noted by the original "delta" above
    private fun updateCache(quad: Quad) {
        // updating the cache pattern-wise
        patterns
            .mapIndexed { i, pattern -> Bitmask.onesAt(i, length = patterns.size) to pattern.delta(quad) }
            .expandResultSet()
            .flatMap { (mask, mappings) ->
                // as we only need to iterate over the patterns not yet managed, we need to inverse the bitmask
                //  before iterating over it
                var currentMask = mask
                mask.inv().fold(mappings) { results, i ->
                    val result = patterns[i].join(results)
                    currentMask = currentMask.withOnesAt(i)
                    patternCache.insert(currentMask, result)
                    result
                }
            }
        // updating the plan union-wise
        unions
            .mapIndexed { i, union -> Bitmask.onesAt(i, length = unions.size) to union.delta(quad) }
            .expandResultSet()
            .flatMap { (mask, mappings) ->
                // as we only need to iterate over the unions not yet managed, we need to inverse the bitmask
                //  before iterating over it
                var currentMask = mask
                mask.inv().fold(mappings) { results, i ->
                    val result = unions[i].join(results)
                    currentMask = currentMask.withOnesAt(i)
                    unionCache.insert(currentMask, result)
                    result
                }
            }
            .let { patterns.fold(it) { results, p -> p.join(results) } }
    }

}
