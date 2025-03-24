package dev.tesserakt.sparql

import dev.tesserakt.rdf.types.MutableStore
import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.sparql.runtime.evaluation.BindingsImpl
import dev.tesserakt.sparql.runtime.evaluation.DataAddition
import dev.tesserakt.sparql.runtime.evaluation.DataDeletion
import dev.tesserakt.sparql.runtime.query.QueryState
import dev.tesserakt.util.replace


class OngoingQueryEvaluationRelease<RT>(private val query: QueryState<RT, *>): OngoingQueryEvaluation<RT> {

    private val _results = mutableMapOf<RT, Int>()
    override val results get() = _results.flatMap { entry -> List(entry.value) { entry.key } }

    private val processor = query.Processor()

    private val listener = object: MutableStore.Listener {
        override fun onQuadAdded(quad: Quad) {
            add(quad)
        }

        override fun onQuadRemoved(quad: Quad) {
            remove(quad)
        }
    }

    init {
        // setting the query state as the current results
        processor.state().forEach {
            _results[it] = 1
        }
    }

    override fun subscribe(store: MutableStore) {
        store.forEach { quad ->
            processor.process(DataAddition(quad)).forEach { process(it) }
        }
        store.addListener(listener)
    }

    override fun unsubscribe(store: MutableStore) {
        store.removeListener(listener)
    }

    override fun add(quad: Quad) {
        processor.process(DataAddition(quad)).forEach { process(it) }
    }

    override fun remove(quad: Quad) {
        processor.process(DataDeletion(quad)).forEach { process(it) }
    }

    override fun debugInformation(): String {
        return processor.debugInformation()
    }

    private fun process(change: QueryState.ResultChange<BindingsImpl>) {
        when (val mapped = query.process(change)) {
            is QueryState.ResultChange.New<*> -> {
                _results.replace(mapped.value) { current -> (current ?: 0) + 1 }
            }
            is QueryState.ResultChange.Removed<*> -> {
                _results.replace(mapped.value) { current -> (current ?: 0) - 1 }
            }
        }
    }

}
