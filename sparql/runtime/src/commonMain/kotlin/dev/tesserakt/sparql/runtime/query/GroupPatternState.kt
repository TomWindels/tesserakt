package dev.tesserakt.sparql.runtime.query

import dev.tesserakt.sparql.runtime.collection.MappingArrayHint
import dev.tesserakt.sparql.runtime.evaluation.*
import dev.tesserakt.sparql.runtime.evaluation.context.QueryContext
import dev.tesserakt.sparql.runtime.query.jointree.JoinTree
import dev.tesserakt.sparql.runtime.query.jointree.from
import dev.tesserakt.sparql.runtime.stream.*
import dev.tesserakt.sparql.types.TriplePatternSet
import dev.tesserakt.sparql.types.Union
import dev.tesserakt.sparql.util.Cardinality

class GroupPatternState(context: QueryContext, pattern: TriplePatternSet, unions: List<Union>): MutableJoinState {

    private val patterns = JoinTree.from(context, pattern)
    private val unions = JoinTree.from(context, unions)

    override val cardinality: Cardinality
        get() = patterns.cardinality * unions.cardinality

    override val bindings = this.patterns.bindings + this.unions.bindings

    init {
        val common = BindingIdentifierSet(context, this.unions.bindings.intersect(this.patterns.bindings))
        val hint = if (pattern.isNotEmpty() && unions.isNotEmpty()) {
            MappingArrayHint(partialHashAccess = true)
        } else {
            MappingArrayHint.DEFAULT
        }
        this.patterns.reindex(common, hint)
        this.unions.reindex(common, hint)
    }

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

    override fun reindex(bindings: BindingIdentifierSet, hint: MappingArrayHint) {
        patterns.reindex(bindings, hint)
        unions.reindex(bindings, hint)
    }

    fun debugInformation() = buildString {
        appendLine("* Patterns")
        append(patterns.debugInformation())
        appendLine("* Unions")
        append(unions.debugInformation())
    }

}
