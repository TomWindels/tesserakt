package dev.tesserakt.sparql

import dev.tesserakt.rdf.types.MutableStore
import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.sparql.runtime.RuntimeStatistics
import dev.tesserakt.sparql.runtime.evaluation.DataAddition
import dev.tesserakt.sparql.runtime.query.QueryState


fun <RT> Iterable<Quad>.query(
    query: Query<RT>,
    callback: (QueryState.ResultChange<RT>) -> Unit
) {
    return query(query.createState(), callback = callback)
}

internal inline fun <RT> Iterable<Quad>.query(
    query: QueryState<RT, *>,
    callback: (QueryState.ResultChange<RT>) -> Unit
) {
    RuntimeStatistics.reset()
    val processor = query.Processor()
    // setting initial state
    processor.state().forEach {
        callback(QueryState.ResultChange.New(it))
    }
    // now incrementally evaluating the input
    val it = iterator()
    while (it.hasNext()) {
        processor.process(DataAddition(it.next())).forEach {
            val mapped = query.process(it)
            callback(mapped)
        }
    }
    RuntimeStatistics.append(processor.debugInformation())
}

fun <RT> Iterable<Quad>.query(query: Query<RT>): List<RT> {
    val queryState = query.createState()
    return query(queryState)
}

internal fun <RT> Iterable<Quad>.query(query: QueryState<RT, *>): List<RT> = buildList {
    RuntimeStatistics.reset()
    val processor = query.Processor()
    // setting initial state
    addAll(processor.state())
    // now incrementally evaluating the input
    val it = this@query.iterator()
    while (it.hasNext()) {
        processor.process(DataAddition(it.next())).forEach {
            when (val mapped = query.process(it)) {
                is QueryState.ResultChange.New<RT> -> add(mapped.value)
                is QueryState.ResultChange.Removed<RT> -> remove(mapped.value)
            }
        }
    }
    RuntimeStatistics.append(processor.debugInformation())
}

fun <RT> MutableStore.query(query: Query<RT>): OngoingQueryEvaluation<RT> {
    return OngoingQueryEvaluation(query.createState()).also { it.subscribe(this) }
}

internal fun <RT> MutableStore.query(query: QueryState<RT, *>): OngoingQueryEvaluation<RT> {
    return OngoingQueryEvaluation(query).also { it.subscribe(this) }
}
