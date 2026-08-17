package dev.tesserakt.sparql.runtime.query

import dev.tesserakt.sparql.QueryStatistics
import dev.tesserakt.sparql.runtime.collection.MappingArrayHint
import dev.tesserakt.sparql.runtime.evaluation.BindingIdentifierSet
import dev.tesserakt.sparql.runtime.evaluation.DataDelta
import dev.tesserakt.sparql.runtime.evaluation.MappingDelta
import dev.tesserakt.sparql.runtime.evaluation.Statistics
import dev.tesserakt.sparql.runtime.evaluation.context.QueryContext
import dev.tesserakt.sparql.runtime.stream.OptimisedStream
import dev.tesserakt.sparql.runtime.stream.Stream
import dev.tesserakt.sparql.types.Filter
import dev.tesserakt.sparql.types.GraphPattern
import dev.tesserakt.sparql.util.Cardinality
import kotlin.jvm.JvmInline

@JvmInline
value class BasicGraphPatternState private constructor(
    private val body: MutableJoinState,
): MutableJoinState {

    /**
     * A collection of all bindings found inside this query body; it is not guaranteed that all solutions generated
     *  through [insert]ion have a value for all of these bindings, as this depends on the query itself
     */
    override val properties: MutableJoinState.Properties
        get() = body.properties

    // we don't check the cardinality after filtering, as doing so would be expensive
    override val cardinality: Cardinality
        get() = body.cardinality

    override fun process(delta: DataDelta): OptimisedStream<MappingDelta> {
        return body.process(delta)
    }

    override fun join(delta: MappingDelta): Stream<MappingDelta> {
        return body.join(delta)
    }

    override fun reindex(
        bindings: BindingIdentifierSet,
        hint: MappingArrayHint
    ) {
        body.reindex(bindings, hint)
    }

    override fun stats(context: QueryContext, granularity: QueryStatistics.Granularity): Statistics {
        return body.stats(context, granularity)
    }

    companion object {

        operator fun invoke(
            context: QueryContext,
            ast: GraphPattern,
            /**
             * In case this is a state that originates from an inner query structure (e.g. part of a union), filters
             *  from outer scopes can be passed here for push down purposes
             */
            externalFilters: List<FilterExpression>,
            /**
             * In case this is a state that originates from an inner query structure (e.g. part of a union), bindings
             *  from outer scopes can be passed here for indexing purposes
             */
            externalBindings: BindingIdentifierSet,
        ): MutableJoinState {
            val filters = ast.filters.mapNotNull { filter ->
                val expression = (filter as? Filter.Predicate)?.expression ?: return@mapNotNull null
                FilterExpression(context, expression)
            } + externalFilters
            val body = BasicGraphBodyState(
                context = context,
                statements = ast.statements,
                filters = filters,
                externalBindings = externalBindings,
            )
            val state = ast.filters.fold(body) { body, filter ->
                when (filter) {
                    is Filter.Predicate -> {
                        // does not alter the state any further, these have been pushed down already
                        body
                    }
                    is Filter.Exists -> {
                        InclusionFilterState(
                            inner = body,
                            filter = BasicGraphPatternState(
                                context = context,
                                ast = filter.pattern,
                                // these aren't passed down
                                externalFilters = emptyList(),
                                externalBindings = BindingIdentifierSet.EMPTY,
                            )
                        )
                    }
                    is Filter.NotExists -> {
                        ExclusionFilterState(
                            inner = body,
                            filter = BasicGraphPatternState(
                                context = context,
                                ast = filter.pattern,
                                // these aren't passed down
                                externalFilters = emptyList(),
                                externalBindings = BindingIdentifierSet.EMPTY,
                            )
                        )
                    }
                }
            }
            return BasicGraphPatternState(
                body = state,
            )
        }

    }

}
