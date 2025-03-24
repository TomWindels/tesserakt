package dev.tesserakt.sparql.runtime.query

import dev.tesserakt.sparql.runtime.evaluation.*
import dev.tesserakt.sparql.runtime.stream.*
import dev.tesserakt.sparql.types.TriplePatternSet
import dev.tesserakt.sparql.types.Union
import dev.tesserakt.sparql.util.Cardinality

class GroupPatternState(context: QueryContext, pattern: TriplePatternSet, unions: List<Union>): MutableJoinState {

    private val patterns = JoinTree(context, pattern)
    private val unions = JoinTree(context, unions)

    override val cardinality: Cardinality
        get() = patterns.cardinality * unions.cardinality

    override val bindings = this.patterns.bindings + this.unions.bindings

    override fun peek(delta: DataDelta): OptimisedStream<MappingDelta> {
        val first = patterns.peek(delta)
        val second = unions.peek(delta)
        // combining these states to get a total set of potential resulting mappings
        return patterns.join(second).chain(unions.join(first)).optimisedForSingleUse()
    }

    override fun process(delta: DataDelta) {
        patterns.process(delta)
        unions.process(delta)
    }

    override fun join(delta: MappingDelta): Stream<MappingDelta> {
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
