package dev.tesserakt.sparql

import dev.tesserakt.rdf.types.ObservableStore
import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.rdf.types.Store
import dev.tesserakt.sparql.evaluation.DeferredOngoingQueryEvaluation
import dev.tesserakt.sparql.evaluation.DeferredOngoingQueryEvaluationImpl
import dev.tesserakt.sparql.evaluation.OngoingQueryEvaluation
import dev.tesserakt.sparql.evaluation.OngoingQueryEvaluationImpl
import dev.tesserakt.sparql.runtime.evaluation.DataAddition
import dev.tesserakt.sparql.runtime.evaluation.Statistics
import dev.tesserakt.sparql.runtime.query.QueryState
import dev.tesserakt.sparql.types.SelectQueryStructure


/**
 * Executes the given [query] on this [Iterable] as data source. Results are issued through the provided [callback].
 * Note that it is possible for results to be removed using [QueryState.ResultChange.Removed] as long as not all data
 *  has been processed.
 *
 * IMPORTANT: the query is not allowed to have solution sequence modifiers (ORDER BY, LIMIT and OFFSET), as the callback
 *  is not aware of result order. Providing such a query will throw a [UnsupportedOperationException] instead.
 */
fun <RT> Iterable<Quad>.query(
    query: Query<RT>,
    callback: (QueryState.ResultChange<RT>) -> Unit
) {
    if (
        query.compiled is SelectQueryStructure && (
            // ORDER BY is not allowed
            query.compiled.ordering != null ||
            // OFFSET is not allowed
            query.compiled.offset != 0 ||
            // LIMIT is not allowed
            query.compiled.limit != Int.MAX_VALUE
        )
    ) {
        throw UnsupportedOperationException("The query contains solution sequence modifiers (ORDER BY, LIMIT and/or OFFSET), which is not supported through this API. Use regular `query()` methods instead, which expose the entire result collection at all times, which do adhere to these solution modifiers, or create a new query without these modifiers.")
    }
    if (this is Store) {
        val state = query.createState(
            // we can hijack the store's context and use the encoded representations directly
            context = this.context,
        )
        // setting initial state
        state.results.forEach {
            callback(QueryState.ResultChange.New(it))
        }
        // now incrementally evaluating the input
        val it = encodedIterator()
        while (it.hasNext()) {
            state
                .processAndGet(DataAddition(it.next()))
                .forEach { callback(it) }
        }
    } else {
        val state = query.createState(
            context = null,
        )
        // setting initial state
        state.results.forEach {
            callback(QueryState.ResultChange.New(it))
        }
        // now incrementally evaluating the input
        val it = iterator()
        while (it.hasNext()) {
            state
                .processAndGetAddition(it.next())
                .forEach { callback(it) }
        }
    }
}

fun <RT> Iterable<Quad>.query(query: Query<RT>): List<RT> {
    val queryState = query.createState(
        // we can hijack the store's context if we are being evaluated on one
        context = if (this is Store) this.context else null,
    )
    return query(queryState)
}

fun <RT> Iterable<Quad>.queryWithStatistics(query: Query<RT>, granularity: QueryStatistics.Granularity): Pair<List<RT>, Statistics> {
    val queryState = query.createState(
        // we can hijack the store's context if we are being evaluated on one
        context = if (this is Store) this.context else null,
    )
    return query(queryState) to queryState.stats(granularity)
}

internal fun <RT> Iterable<Quad>.query(query: QueryState<RT, *>): List<RT> {
    if (this is Store) {
        // all possible paths that called `query` have constructed the state using the encoding context found in the
        //  receiver (store context), so we can iterate over the encoded representations directly
        val it = this@query.encodedIterator()
        while (it.hasNext()) {
            query.process(DataAddition(it.next()))
        }
    } else {
        // we've created an encoding context specific for this query: we need to use the context to encode the elements
        //  on the fly
        val it = this@query.iterator()
        while (it.hasNext()) {
            query.processAddition(it.next())
        }
    }
    return query.results.toList()
}

fun <RT> ObservableStore.query(query: Query<RT>): OngoingQueryEvaluation<RT> {
    return OngoingQueryEvaluationImpl(query.createState(context)).also { it.subscribe(this) }
}

fun <RT> ObservableStore.queryDeferred(query: Query<RT>): DeferredOngoingQueryEvaluation<RT> {
    return DeferredOngoingQueryEvaluationImpl(query.createState(context)).also { it.subscribe(this) }
}

/* helper properties */

val Query<Bindings>.variables: Set<String>
    get() = (compiled as SelectQueryStructure).bindings
