package dev.tesserakt.sparql.runtime.incremental.evaluation

import dev.tesserakt.rdf.types.MutableStore
import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.sparql.runtime.common.util.Debug
import dev.tesserakt.sparql.runtime.incremental.delta.Delta
import dev.tesserakt.sparql.runtime.incremental.query.IncrementalQuery


fun <RT> Iterable<Quad>.query(query: IncrementalQuery<RT, *>, callback: (IncrementalQuery.ResultChange<RT>) -> Unit) {
    Debug.reset()
    val processor = query.Processor()
    val it = iterator()
    while (it.hasNext()) {
        processor.process(Delta.DataAddition(it.next())).forEach {
            val mapped = query.process(it)
            callback(mapped)
        }
    }
    Debug.append(processor.debugInformation())
}

fun <RT> Iterable<Quad>.query(query: IncrementalQuery<RT, *>): List<RT> = buildList {
    Debug.reset()
    val processor = query.Processor()
    val it = this@query.iterator()
    while (it.hasNext()) {
        processor.process(Delta.DataAddition(it.next())).forEach {
            when (val mapped = query.process(it)) {
                is IncrementalQuery.ResultChange.New<RT> -> add(mapped.value)
                is IncrementalQuery.ResultChange.Removed<RT> -> remove(mapped.value)
            }
        }
    }
    Debug.append(processor.debugInformation())
}

fun <RT> MutableStore.query(query: IncrementalQuery<RT, *>): OngoingQueryEvaluation<RT> {
    return OngoingQueryEvaluation(query).also { it.subscribe(this) }
}