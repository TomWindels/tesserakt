package dev.tesserakt.sparql.runtime.incremental.state

import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.sparql.runtime.core.Mapping
import dev.tesserakt.sparql.runtime.incremental.state.IncrementalTriplePatternState.Companion.createIncrementalPatternState
import dev.tesserakt.sparql.runtime.incremental.types.Query
import dev.tesserakt.sparql.runtime.util.Bitmask

internal class IncrementalBasicGraphPatternState(ast: Query.QueryBody) {

    private val bodyTree = JoinTree.LeftDeep()
    private val unionTree = JoinTree.LeftDeep()
    private val patterns = ast.patterns
        // sorting the patterns based on the used join tree implementation
        .let { bodyTree.sorted(it) }
        // and converting them into separate states, retaining that order
        .map { it.createIncrementalPatternState() }
    private val unions = ast.unions.map { union -> IncrementalUnionState(union) }

    fun delta(quad: Quad): List<Mapping> {
        val base = with (bodyTree) {
            patterns
                .mapIndexed { i, pattern -> Bitmask.onesAt(i, length = patterns.size) to pattern.delta(quad) }
                .expandResultSet()
                .growUsingCache()
                .flatMap { (mask, mappings) ->
                    // as we only need to iterate over the patterns not yet managed, we need to inverse the bitmask
                    //  before iterating over it
                    mask.inv().fold(mappings) { results, i -> if (results.isEmpty()) return@flatMap emptyList() else patterns[i].join(results) }
                }
        }
        val first = unions.fold(initial = base) { results, u -> u.join(results) }
        return first + with (unionTree) {
            unions
                .mapIndexed { i, union -> Bitmask.onesAt(i, length = unions.size) to union.delta(quad) }
                .expandResultSet()
                .growUsingCache()
                .flatMap { (mask, mappings) ->
                    // as we only need to iterate over the unions not yet managed, we need to inverse the bitmask
                    //  before iterating over it
                    mask.inv().fold(mappings) { results, i -> unions[i].join(results) }
                }
                .let { patterns.fold(it) { results, p -> p.join(results) } }
        }
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
        with (bodyTree) {
            patterns
                .mapIndexed { i, pattern -> Bitmask.onesAt(i, length = patterns.size) to pattern.delta(quad) }
                .expandResultSet()
                .growUsingCache()
                .flatMap { (mask, mappings) ->
                    // inserting the new item(s) first - this iteration can be the result from `expandResultSet` and
                    //  satisfy multiple triple patterns already
                    bodyTree.insert(mask, mappings)
                    // as we only need to iterate over the patterns not yet managed, we need to inverse the bitmask
                    //  before iterating over it
                    var currentMask = mask
                    mask.inv().fold(mappings) { results, i ->
                        val result = patterns[i].join(results)
                        // TODO: early bailout
                        currentMask = currentMask.withOnesAt(i)
                        bodyTree.insert(currentMask, result)
                        result
                    }
                }
        }
        // updating the plan union-wise
        with (unionTree) {
            unions
                .mapIndexed { i, union -> Bitmask.onesAt(i, length = unions.size) to union.delta(quad) }
                .expandResultSet()
                .growUsingCache()
                .flatMap { (mask, mappings) ->
                    // inserting the new item(s) first - this iteration can be the result from `expandResultSet` and
                    //   satisfy multiple unions already
                    unionTree.insert(mask, mappings)
                    // as we only need to iterate over the unions not yet managed, we need to inverse the bitmask
                    //  before iterating over it
                    var currentMask = mask
                    mask.inv().fold(mappings) { results, i ->
                        val result = unions[i].join(results)
                        currentMask = currentMask.withOnesAt(i)
                        unionTree.insert(currentMask, result)
                        result
                    }
                }
                .let { patterns.fold(it) { results, p -> p.join(results) } }
        }
    }

}
