package dev.tesserakt.sparql.runtime.incremental.evaluation

import dev.tesserakt.rdf.types.MutableStore
import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.sparql.runtime.common.types.Bindings
import dev.tesserakt.sparql.runtime.incremental.delta.Delta
import dev.tesserakt.sparql.runtime.incremental.query.IncrementalQuery


class OngoingQueryEvaluation<RT>(private val query: IncrementalQuery<RT, *>) {

    private val _results = mutableListOf<RT>()
    val results get() = _results.toList()

    private val processor = query.Processor()

    private val listener = object: MutableStore.Listener {
        override fun onQuadAdded(quad: Quad) {
            add(quad)
        }

        override fun onQuadRemoved(quad: Quad) {
            remove(quad)
        }
    }

    fun subscribe(store: MutableStore) {
        store.forEach { quad ->
            processor.process(Delta.DataAddition(quad)).forEach { process(it) }
        }
        store.addListener(listener)
    }

    fun unsubscribe(store: MutableStore) {
        store.removeListener(listener)
    }

    fun add(quad: Quad) {
        processor.process(Delta.DataAddition(quad)).forEach { process(it) }
    }

    fun remove(quad: Quad) {
        processor.process(Delta.DataDeletion(quad)).forEach { process(it) }
    }

    private fun process(change: IncrementalQuery.ResultChange<Bindings>) {
        when (val mapped = query.process(change)) {
            is IncrementalQuery.ResultChange.New<*> -> _results.add(mapped.value)
            is IncrementalQuery.ResultChange.Removed<*> -> _results.remove(mapped.value)
        }
    }

}
