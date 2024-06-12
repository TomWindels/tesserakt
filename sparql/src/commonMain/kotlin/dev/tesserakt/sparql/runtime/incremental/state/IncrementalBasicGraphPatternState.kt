package dev.tesserakt.sparql.runtime.incremental.state

import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.sparql.runtime.core.Mapping
import dev.tesserakt.sparql.runtime.incremental.pattern.IncrementalBasicGraphPattern
import dev.tesserakt.sparql.runtime.incremental.state.IncrementalTriplePatternState.Companion.createIncrementalPatternState
import dev.tesserakt.sparql.runtime.util.Bitmask

internal class IncrementalBasicGraphPatternState(parent: IncrementalBasicGraphPattern) {

    private val patterns = parent.patterns.map { it.createIncrementalPatternState() }
    private val unions = parent.unions.map { union -> IncrementalUnionState(union) }

    fun delta(quad: Quad): List<Mapping> {
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
        patterns.forEach { it.insert(quad) }
        unions.forEach { it.insert(quad) }
    }

    fun join(mappings: List<Mapping>): List<Mapping> = unions
        .fold(initial = patterns.fold(mappings) { results, p -> p.join(results) }) { results, u -> u.join(results) }

}
