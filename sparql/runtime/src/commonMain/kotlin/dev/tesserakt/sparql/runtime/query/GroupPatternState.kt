package dev.tesserakt.sparql.runtime.query

import dev.tesserakt.sparql.runtime.evaluation.DataDelta
import dev.tesserakt.sparql.runtime.evaluation.MappingAddition
import dev.tesserakt.sparql.runtime.evaluation.MappingDeletion
import dev.tesserakt.sparql.runtime.evaluation.MappingDelta
import dev.tesserakt.sparql.runtime.stream.Stream
import dev.tesserakt.sparql.runtime.stream.chain
import dev.tesserakt.sparql.runtime.stream.join
import dev.tesserakt.sparql.runtime.stream.optimisedForSingleUse
import dev.tesserakt.sparql.types.TriplePatternSet
import dev.tesserakt.sparql.types.Union
import dev.tesserakt.sparql.util.Cardinality

class GroupPatternState(pattern: TriplePatternSet, unions: List<Union>) {

    private val patterns = JoinTree(pattern)
    private val unions = JoinTree(unions)

    val cardinality: Cardinality
        get() = patterns.cardinality * unions.cardinality

    val bindings = this.patterns.bindings + this.unions.bindings

    fun peek(delta: DataDelta): Stream<MappingDelta> {
        val first = patterns.peek(delta)
        val second = unions.peek(delta)
        // combining these states to get a total set of potential resulting mappings
        return patterns.join(second).chain(unions.join(first))
    }

    fun process(delta: DataDelta) {
        patterns.process(delta)
        unions.process(delta)
    }

    fun join(delta: MappingDelta): Stream<MappingDelta> {
        return unions.join(patterns.join(delta).optimisedForSingleUse())
    }

    fun join(delta: MappingAddition): Stream<MappingAddition> {
        // this is guaranteed behaviour for a set of triple patterns / unions
        @Suppress("UNCHECKED_CAST")
        return unions.join(patterns.join(delta).optimisedForSingleUse()) as Stream<MappingAddition>
    }

    fun join(delta: MappingDeletion): Stream<MappingDeletion> {
        // this is guaranteed behaviour for a set of triple patterns / unions
        @Suppress("UNCHECKED_CAST")
        return unions.join(patterns.join(delta).optimisedForSingleUse()) as Stream<MappingDeletion>
    }

    fun debugInformation() = buildString {
        appendLine("* Patterns")
        append(patterns.debugInformation())
        appendLine("* Unions")
        append(unions.debugInformation())
    }

}
