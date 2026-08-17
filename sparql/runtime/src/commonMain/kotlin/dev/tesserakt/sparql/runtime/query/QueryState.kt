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
import dev.tesserakt.sparql.runtime.evaluation.mapping.hashable
import dev.tesserakt.sparql.runtime.stream.CollectedStream
import dev.tesserakt.sparql.runtime.stream.Stream
import dev.tesserakt.sparql.types.*
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
        }

    }

    /**
     * Based on the query body definition, it's possible mandatory stream post-processing is required to prevent
     *  results temporarily going negative during evaluation
     */
    sealed interface StreamPostProcessor {

        fun adapt(stream: Stream<MappingDelta>): Stream<MappingDelta>

        data object None: StreamPostProcessor {
            override fun adapt(stream: Stream<MappingDelta>): Stream<MappingDelta> {
                return stream
            }
        }

        data object Reordered: StreamPostProcessor {
            override fun adapt(stream: Stream<MappingDelta>): Stream<MappingDelta> {
                val combined = stream
                    // we need to make it hashable for `groupingBy` to work correctly
                    .groupingBy { it.value.hashable() }
                    .fold({ _, _ -> 0 }) { _, count, delta ->
                        val d = if (delta is MappingAddition) 1 else -1
                        count + d
                    }
                return CollectedStream(
                    // TODO perf:
                    //  simply return another Iterable, so we don't need to create a potentially big array at the end
                    data = combined.asIterable().flatMap { (mapping, count) ->
                        when {
                            count == 0 -> emptyList()
                            count > 0 -> {
                                List(count) { MappingAddition(mapping.inner) }
                            }
                            else -> {
                                List(-count) { MappingDeletion(mapping.inner) }
                            }
                        }
                    }
                )
            }
        }

    }

    private val streamPostProcessor = when {
        ast.body.requiresReordering() -> StreamPostProcessor.Reordered
        else -> StreamPostProcessor.None
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
        return bgpState
            .process(data)
            .let { streamPostProcessor.adapt(it) }
            .onEach(::onNewBodyResult)
            .map(::transformNewBodyResult)
    }

    fun process(data: DataDelta) {
        bgpState
            .process(data)
            .let { streamPostProcessor.adapt(it) }
            .onEach(::onNewBodyResult)
    }

    // can't be done during `init {}` of this base class as it requires the concrete implementation for `onNewBodyResult`
    protected fun constructInitialState() {
        // required when setting up the initial state: sets up initial state
        //  combinations (i.e. triple patterns such as "?a <p>* <b>", yielding ?a = <b>)
        bgpState
            // getting all current results by joining with an empty new mapping
            .join(MappingAddition(Mapping.EMPTY))
            .let { streamPostProcessor.adapt(it) }
            .forEach(::onNewBodyResult)
    }

    protected abstract fun onNewBodyResult(change: MappingDelta)

    protected abstract fun transformNewBodyResult(change: MappingDelta): ResultChange<ResultType>

    fun stats(granularity: QueryStatistics.Granularity): Statistics {
        return bgpState.stats(context, granularity)
    }

    private fun GraphPattern.requiresReordering(): Boolean {
        fun Union.requiresReordering(): Boolean {
            return segments.any { segment ->
                when (segment) {
                    is GraphPatternSegment -> segment.pattern.requiresReordering()
                    is SelectQuerySegment -> false
                }
            }
        }
        return filters.any { it !is Filter.Predicate } ||
                statements.any { it is Optional || (it is Union && it.requiresReordering()) }
    }

}
