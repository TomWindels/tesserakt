package dev.tesserakt.sparql

import dev.tesserakt.rdf.types.ObservableStore
import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.sparql.evaluation.DeferredOngoingQueryEvaluation
import dev.tesserakt.sparql.evaluation.DeferredOngoingQueryEvaluationImpl
import dev.tesserakt.sparql.evaluation.OngoingQueryEvaluation
import dev.tesserakt.sparql.evaluation.OngoingQueryEvaluationImpl
import dev.tesserakt.sparql.runtime.RuntimeStatistics
import dev.tesserakt.sparql.runtime.evaluation.DataAddition
import dev.tesserakt.sparql.runtime.query.QueryState
import dev.tesserakt.sparql.types.SelectQueryStructure


/**
 * Executes the given [query] on this [Iterable] as data source. Results are issued through the provided [callback].
 * Note that it is possible for results to be removed using [QueryState.ResultChange.Removed] as long as not all data
 *  has been processed.
 *
 * IMPORTANT: the query is not allowed to use `ORDER BY`, as the callback is not aware of result order. Providing such
 *  a query will throw a [UnsupportedOperationException] instead.
 */
fun <RT> Iterable<Quad>.query(
    query: Query<RT>,
    callback: (QueryState.ResultChange<RT>) -> Unit
) {
    if (query.compiled is SelectQueryStructure && query.compiled.ordering != null) {
        throw UnsupportedOperationException("The query uses `ORDER BY`, which is not supported through this API! Use regular `query()` methods instead.")
    }
    val state = query.createState()
    RuntimeStatistics.reset()
    // setting initial state
    state.results.forEach {
        callback(QueryState.ResultChange.New(it))
    }
    // now incrementally evaluating the input
    val it = iterator()
    while (it.hasNext()) {
        state
            .processAndGet(DataAddition(it.next()))
            .forEach { callback(it) }
    }
    RuntimeStatistics.append(state.debugInformation())
}

fun <RT> Iterable<Quad>.query(query: Query<RT>): List<RT> {
    val queryState = query.createState()
    return query(queryState)
}

internal fun <RT> Iterable<Quad>.query(query: QueryState<RT, *>): List<RT> {
    RuntimeStatistics.reset()
    // now incrementally evaluating the input
    val it = this@query.iterator()
    while (it.hasNext()) {
        query.process(DataAddition(it.next()))
    }
    RuntimeStatistics.append(query.debugInformation())
    return query.results.toList()
}

fun <RT> ObservableStore.query(query: Query<RT>): OngoingQueryEvaluation<RT> {
    return OngoingQueryEvaluationImpl(query.createState()).also { it.subscribe(this) }
}

fun <RT> ObservableStore.queryDeferred(query: Query<RT>): DeferredOngoingQueryEvaluation<RT> {
    return DeferredOngoingQueryEvaluationImpl(query.createState()).also { it.subscribe(this) }
}

internal fun <RT> ObservableStore.query(query: QueryState<RT, *>): OngoingQueryEvaluation<RT> {
    return OngoingQueryEvaluationImpl(query).also { it.subscribe(this) }
}

/* helper properties */

val Query<Bindings>.variables: Set<String>
    get() = (compiled as SelectQueryStructure).bindings
