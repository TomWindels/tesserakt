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

            /**
             * Materializes a solution into a representation that has its binding names and term values decoded.
             * Also executes [expressions] that require the full term values, resulting in new terms not necessarily
             *  present in the accompanying [QueryContext].
             */
            inline fun Mapping.materialize(
                context: QueryContext,
                expressions: Collection<BindingExpression>,
            ) = BindingsImpl(context, this, expressions)

        }

    }

    protected val context = QueryContext(source, ast)
    protected val bgpState = BasicGraphPatternState(
        context = context,
        ast = Compat.apply(ast.body),
        // this is the most top-level state, so there isn't any external source to obtain filters from
        externalFilters = emptyList(),
        // this also means there aren't any other 'external' bindings
        externalBindings = BindingIdentifierSet.EMPTY,
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

    fun processAndGet(data: DataDelta): List<ResultChange<ResultType>> {
        bgpState.enqueue(data)
        return bgpState
            .process()
            .onEach(::onNewBodyResult)
            .map(::transformNewBodyResult)
    }

    fun process(data: DataDelta) {
        bgpState.enqueue(data)
        bgpState
            .process()
            .onEach(::onNewBodyResult)
    }

    fun processAndGet(changes: Iterable<DataDelta>): List<ResultChange<ResultType>> {
        changes.forEach { data ->
            bgpState.enqueue(data)
        }
        return bgpState
            .process()
            .onEach(::onNewBodyResult)
            .map(::transformNewBodyResult)
    }

    fun process(changes: Iterable<DataDelta>) {
        changes.forEach { data ->
            bgpState.enqueue(data)
        }
        bgpState
            .process()
            .onEach(::onNewBodyResult)
    }

    // can't be done during `init {}` of this base class as it requires the concrete implementation for `onNewBodyResult`
    protected fun constructInitialState() {
        // required when setting up the initial state: sets up initial state
        //  combinations (i.e. triple patterns such as "?a <p>* <b>", yielding ?a = <b>)
        bgpState
            // getting all current results by joining with an empty new mapping
            .join(MappingAddition(Mapping.EMPTY))
            .forEach(::onNewBodyResult)
    }

    protected abstract fun onNewBodyResult(change: MappingDelta)

    protected abstract fun transformNewBodyResult(change: MappingDelta): ResultChange<ResultType>

    open fun stats(granularity: QueryStatistics.Granularity): Statistics {
        return bgpState.stats(context, granularity)
    }

}
