package dev.tesserakt.sparql.runtime.query

import dev.tesserakt.sparql.QueryStatistics
import dev.tesserakt.sparql.runtime.collection.MappingArrayHint
import dev.tesserakt.sparql.runtime.evaluation.*
import dev.tesserakt.sparql.runtime.evaluation.context.QueryContext
import dev.tesserakt.sparql.runtime.query.jointree.EmptyJoinTree
import dev.tesserakt.sparql.runtime.query.jointree.JoinTree
import dev.tesserakt.sparql.runtime.query.jointree.from
import dev.tesserakt.sparql.runtime.stream.*
import dev.tesserakt.sparql.types.TriplePatternSet
import dev.tesserakt.sparql.types.Union
import dev.tesserakt.sparql.types.extractAllBindings
import dev.tesserakt.sparql.util.Cardinality

class GroupPatternState private constructor(
    private val patterns: MutableJoinState,
    private val unions: MutableJoinState,
    private val filters: List<FilterExpression>,
): MutableJoinState {

    override val cardinality: Cardinality
        get() = patterns.cardinality * unions.cardinality

    override val bindings = this.patterns.bindings + this.unions.bindings

    init {
        val common = this.unions.bindings.intersect(this.patterns.bindings)
        val hint = if (patterns !is EmptyJoinTree && unions !is EmptyJoinTree) {
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
        return patterns
            .join(second).chain(unions.join(first))
            .filtered { mapping -> filters.all { filter -> filter.test(mapping.value) } }
            .optimisedForSingleUse()
    }

    override fun process(delta: DataDelta) {
        patterns.process(delta)
        unions.process(delta)
    }

    override fun join(delta: MappingDelta): Stream<MappingDelta> {
        return unions
            .join(patterns.join(delta).optimisedForSingleUse())
            .filtered { mapping -> filters.all { filter -> filter.test(mapping.value) } }
    }

    fun join(delta: MappingAddition): Stream<MappingAddition> {
        // this is guaranteed behaviour for a set of triple patterns / unions
        @Suppress("UNCHECKED_CAST")
        return unions
            .join(patterns.join(delta).optimisedForSingleUse())
            .filtered { mapping -> filters.all { filter -> filter.test(mapping.value) } } as Stream<MappingAddition>
    }

    fun join(delta: MappingDeletion): Stream<MappingDeletion> {
        // this is guaranteed behaviour for a set of triple patterns / unions
        @Suppress("UNCHECKED_CAST")
        return unions
            .join(patterns.join(delta).optimisedForSingleUse())
            .filtered { mapping -> filters.all { filter -> filter.test(mapping.value) } } as Stream<MappingDeletion>
    }

    override fun reindex(bindings: BindingIdentifierSet, hint: MappingArrayHint) {
        patterns.reindex(bindings, hint)
        unions.reindex(bindings, hint)
    }

    override fun stats(context: QueryContext, granularity: QueryStatistics.Granularity): Statistics {
        val inner = Statistics.JoinedElement(left = patterns.stats(context, granularity), right = unions.stats(context, granularity))
        return if (granularity isAtLeast QueryStatistics.Granularity.DETAILED && filters.isNotEmpty()) {
            Statistics.DescriptionElement(
                description = "Filtered\n${this.filters.joinToString("\n")}",
                inner = inner,
            )
        } else {
            inner
        }
    }

    companion object {

        operator fun invoke(
            context: QueryContext,
            pattern: TriplePatternSet,
            unions: List<Union>,
            filters: List<FilterExpression>,
        ): MutableJoinState {
            // we create both parts separately
            val patterns = JoinTree.from(
                context = context,
                patterns = pattern,
                filters = filters,
                externalBindings = unions.fold(
                    BindingIdentifierSet.EMPTY
                ) { bindings, union ->
                    bindings + union.segments.fold(BindingIdentifierSet.EMPTY) { bindings, segment ->
                        bindings + BindingIdentifierSet(
                            context = context,
                            names = segment.extractAllBindings().mapTo(mutableSetOf()) { it.name }
                        )
                    }
                }
            )
            val unions = JoinTree.from(
                context = context,
                unions = unions,
                filters = filters,
                externalBindings = patterns.bindings,
            )
            // we then apply all filters that can only be combined on the entire group on top of it
            val topLevelFilters = filters.filter { expression ->
                // we retain those that have overlap with both the patterns and the union query segments
                val patternOverlap = expression.bindings.intersectSize(patterns.bindings)
                val unionOverlap = expression.bindings.intersectSize(unions.bindings)
                // both should have at least one of the expression bindings, and at least one of them should be
                //  incomplete without the other's bindings in scope
                patternOverlap != 0 && unionOverlap != 0 &&
                (patternOverlap != expression.bindings.size || unionOverlap != expression.bindings.size)
            }
            return GroupPatternState(
                patterns = patterns,
                unions = unions,
                filters = topLevelFilters,
            )
        }

    }

}
