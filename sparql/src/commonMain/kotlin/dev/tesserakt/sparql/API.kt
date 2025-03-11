package dev.tesserakt.sparql

import dev.tesserakt.rdf.types.MutableStore
import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.sparql.debug.Debug
import dev.tesserakt.sparql.types.runtime.evaluation.DataAddition
import dev.tesserakt.sparql.types.runtime.evaluation.OngoingQueryEvaluation
import dev.tesserakt.sparql.types.runtime.query.Query


fun <RT> Iterable<Quad>.query(query: Query<RT, *>, callback: (Query.ResultChange<RT>) -> Unit) {
    Debug.reset()
    val processor = query.Processor()
    // setting initial state
    processor.state().forEach {
        callback(Query.ResultChange.New(it))
    }
    // now incrementally evaluating the input
    val it = iterator()
    while (it.hasNext()) {
        processor.process(DataAddition(it.next())).forEach {
            val mapped = query.process(it)
            callback(mapped)
        }
    }
    Debug.append(processor.debugInformation())
}

fun <RT> Iterable<Quad>.query(query: Query<RT, *>): List<RT> = buildList {
    Debug.reset()
    val processor = query.Processor()
    // setting initial state
    addAll(processor.state())
    // now incrementally evaluating the input
    val it = this@query.iterator()
    while (it.hasNext()) {
        processor.process(DataAddition(it.next())).forEach {
            when (val mapped = query.process(it)) {
                is Query.ResultChange.New<RT> -> add(mapped.value)
                is Query.ResultChange.Removed<RT> -> remove(mapped.value)
            }
        }
    }
    Debug.append(processor.debugInformation())
}

fun <RT> MutableStore.query(query: Query<RT, *>): OngoingQueryEvaluation<RT> {
    return OngoingQueryEvaluation(query).also { it.subscribe(this) }
}
