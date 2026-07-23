package dev.tesserakt.sparql.runtime.query.jointree

import dev.tesserakt.sparql.QueryStatistics
import dev.tesserakt.sparql.runtime.collection.MappingArrayHint
import dev.tesserakt.sparql.runtime.evaluation.BindingIdentifierSet
import dev.tesserakt.sparql.runtime.evaluation.DataDelta
import dev.tesserakt.sparql.runtime.evaluation.MappingDelta
import dev.tesserakt.sparql.runtime.evaluation.Statistics
import dev.tesserakt.sparql.runtime.evaluation.context.QueryContext
import dev.tesserakt.sparql.runtime.query.FilterExpression
import dev.tesserakt.sparql.runtime.query.MutableJoinState
import dev.tesserakt.sparql.runtime.query.TriplePatternState
import dev.tesserakt.sparql.runtime.query.UnionState
import dev.tesserakt.sparql.runtime.stream.OptimisedStream
import dev.tesserakt.sparql.runtime.stream.Stream
import dev.tesserakt.sparql.types.TriplePattern
import dev.tesserakt.sparql.types.Union
import dev.tesserakt.sparql.util.Cardinality
import kotlin.jvm.JvmInline
import kotlin.jvm.JvmName

@JvmInline
value class SingleItemJoinTree<J: MutableJoinState>(private val element: J): JoinTree {

    override val bindings: BindingIdentifierSet
        get() = element.bindings

    override val cardinality: Cardinality
        get() = element.cardinality

    init {
        // the internal element should have no indexes as we don't have joining with other elements
        element.reindex(BindingIdentifierSet.EMPTY, MappingArrayHint.DEFAULT)
    }

    override fun peek(delta: DataDelta): OptimisedStream<MappingDelta> {
        return element.peek(delta)
    }

    override fun process(delta: DataDelta) {
        element.process(delta)
    }

    override fun join(delta: MappingDelta): Stream<MappingDelta> {
        return element.join(delta)
    }

    override fun stats(context: QueryContext, granularity: QueryStatistics.Granularity): Statistics {
        return element.stats(context, granularity)
    }

    override fun reindex(
        bindings: BindingIdentifierSet,
        hint: MappingArrayHint
    ) {
        // we can pass this to the inner element directly; it's part of a larger structure
        //  that can benefit from specific indexes
        element.reindex(bindings, hint)
    }

    override fun filtered(filter: FilterExpression): MutableJoinState {
        // we have to make sure the filter expression fits in the single join state we wrap, as
        //  otherwise the expression could not be properly processed within this tree, and we cannot
        //  apply the filter
        if (filter.bindings !in this.bindings) {
            return this
        }
        return SingleItemJoinTree(
            element = element.filtered(filter),
        )
    }

    companion object {

        @JvmName("forPatterns")
        operator fun invoke(
            context: QueryContext,
            patterns: List<TriplePattern>,
            filters: List<FilterExpression>,
        ) = SingleItemJoinTree(
            element = patterns.single().let {
                var root = TriplePatternState.from(context, it)
                // there is no in-between state: either our only node is affected by the filter, or it isn't;
                //  we apply the filter directly if it is
                val bindings = root.bindings
                filters.forEach { expression ->
                    if (expression.bindings in bindings) {
                        root = root.filtered(expression)
                    }
                }
                root
            }
        )

        @JvmName("forPatternStates")
        operator fun invoke(
            patterns: List<TriplePatternState<*>>,
            filters: List<FilterExpression>,
        ) = SingleItemJoinTree(
            element = patterns.single().let {
                var root = it
                // there is no in-between state: either our only node is affected by the filter, or it isn't;
                //  we apply the filter directly if it is
                val bindings = root.bindings
                filters.forEach { expression ->
                    if (expression.bindings in bindings) {
                        root = root.filtered(expression)
                    }
                }
                root
            }
        )

        @JvmName("forUnions")
        operator fun invoke(
            context: QueryContext,
            unions: List<Union>,
            filters: List<FilterExpression>,
        ) = SingleItemJoinTree(
            element = unions.single().let {
                // we immediately propagate the filter expressions downwards
                UnionState(context = context, union = it, filters = filters)
            }
        )

    }

}
