package dev.tesserakt.sparql

import dev.tesserakt.rdf.types.ObservableStore
import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.rdf.types.Store
import dev.tesserakt.sparql.evaluation.DeferredOngoingQueryEvaluation
import dev.tesserakt.sparql.evaluation.DeferredOngoingQueryEvaluationImpl
import dev.tesserakt.sparql.evaluation.OngoingQueryEvaluation
import dev.tesserakt.sparql.evaluation.OngoingQueryEvaluationImpl
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
            source = this,
        )
        // we immediately get the results
        state.results.forEach {
            callback(QueryState.ResultChange.New(it))
        }
    } else {
        val state = query.createState(
            source = null,
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
    val queryState = when {
        this is Store -> {
            // we can tie the store instance directly to the (temporary) query state; this allows
            //  the query state to interface with the store directly in two key ways:
            //  * reuse the store's encoding context
            //  * using indexes (if any) to do targeted lookup of the store contents;
            //  * use the results of the targeted lookups to do query planning;
            query.createState(
                source = this,
            )
        }
        else -> {
            // we've created an encoding context specific for this query: we need to use the context to encode the elements
            //  on the fly
            val state = query.createState(
                source = null,
            )
            // we also need to feed the query state all data of this source iterable
            val it = this@query.iterator()
            while (it.hasNext()) {
                state.processAddition(it.next())
            }
            state
        }
    }
    return queryState.results.toList()
}

fun <RT> Iterable<Quad>.queryWithStatistics(
    query: Query<RT>,
    granularity: QueryStatistics.Granularity = QueryStatistics.Granularity.DETAILED,
): Pair<List<RT>, QueryStatistics> {
    val queryState = when {
        this is Store -> {
            // we can tie the store instance directly to the (temporary) query state; this allows
            //  the query state to interface with the store directly in two key ways:
            //  * reuse the store's encoding context
            //  * using indexes (if any) to do targeted lookup of the store contents;
            //  * use the results of the targeted lookups to do query planning;
            query.createState(
                source = this,
            )
        }
        else -> {
            // we've created an encoding context specific for this query: we need to use the context to encode the elements
            //  on the fly
            val state = query.createState(
                source = null,
            )
            // we also need to feed the query state all data of this source iterable
            val it = this@queryWithStatistics.iterator()
            while (it.hasNext()) {
                state.processAddition(it.next())
            }
            state
        }
    }
    return queryState.results.toList() to queryState.stats(granularity)
}

fun <RT> ObservableStore.query(query: Query<RT>): OngoingQueryEvaluation<RT> {
    return OngoingQueryEvaluationImpl(
        parent = this,
        query = query.createState(this),
    )
}

fun <RT> ObservableStore.queryDeferred(query: Query<RT>): DeferredOngoingQueryEvaluation<RT> {
    return DeferredOngoingQueryEvaluationImpl(
        parent = this,
        query = query,
    )
}

/* helper properties */

val Query<Bindings>.variables: Set<String>
    get() = (compiled as SelectQueryStructure).bindings
