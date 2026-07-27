package dev.tesserakt.sparql.runtime.query

import dev.tesserakt.rdf.types.EncodingContext
import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.rdf.types.Store
import dev.tesserakt.sparql.QueryStatistics
import dev.tesserakt.sparql.runtime.compat.Compat
import dev.tesserakt.sparql.runtime.evaluation.*
import dev.tesserakt.sparql.runtime.evaluation.context.QueryContext
import dev.tesserakt.sparql.runtime.evaluation.context.encode
import dev.tesserakt.sparql.runtime.evaluation.mapping.Mapping
import dev.tesserakt.sparql.types.QueryStructure
import kotlin.jvm.JvmInline

sealed class QueryState<ResultType, Q: QueryStructure>(
    protected val ast: Q,
    source: Store? = null,
) {

    sealed interface ResultChange<out T> {

        val value: T

        @JvmInline
        value class New<T>(override val value: T): ResultChange<T>
        @JvmInline
        value class Removed<T>(override val value: T): ResultChange<T>

        companion object {
            inline fun Mapping.into(context: QueryContext) = BindingsImpl(context, this)

            inline fun MappingDelta.asResultChange() = when (this) {
                is MappingAddition -> New(value)
                is MappingDeletion -> Removed(value)
            }

            inline fun MappingDelta.asResultChange(context: QueryContext) = when (this) {
                is MappingAddition -> New(value.into(context))
                is MappingDeletion -> Removed(value.into(context))
            }
        }

    }

    protected val context = QueryContext(source, ast)
    protected val bgpState = BasicGraphPatternState(
        context = context,
        ast = Compat.apply(ast.body),
        // this is the most top-level state, so there isn't any external source to obtain filters from
        externalFilters = emptyList(),
    )

    abstract val results: Collection<ResultType>

    /**
     * A convenience method to use [processAndGet] without having access to
     *  the underlying [QueryContext] / [EncodingContext]
     */
    fun processAndGetAddition(quad: Quad): List<ResultChange<ResultType>> {
        return processAndGet(DataAddition(context.encode(quad)))
    }

    /**
     * A convenience method to use [process] without having access to
     *  the underlying [QueryContext] / [EncodingContext]
     */
    fun processAddition(quad: Quad) {
        return process(DataAddition(context.encode(quad)))
    }

    abstract fun processAndGet(data: DataDelta): List<ResultChange<ResultType>>

    abstract fun process(data: DataDelta)

    fun stats(granularity: QueryStatistics.Granularity): Statistics {
        return bgpState.stats(context, granularity)
    }

}
