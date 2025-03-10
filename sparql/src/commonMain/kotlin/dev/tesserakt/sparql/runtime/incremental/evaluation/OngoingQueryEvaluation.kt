package dev.tesserakt.sparql.runtime.incremental.evaluation

import dev.tesserakt.rdf.types.MutableStore
import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.sparql.runtime.common.types.Bindings
import dev.tesserakt.sparql.runtime.incremental.delta.DataAddition
import dev.tesserakt.sparql.runtime.incremental.delta.DataDeletion
import dev.tesserakt.sparql.runtime.incremental.query.IncrementalQuery
import dev.tesserakt.util.replace


class OngoingQueryEvaluation<RT>(private val query: IncrementalQuery<RT, *>) {

    private val _results = mutableMapOf<RT, Int>()
    val results get() = _results.flatMap { entry -> List(entry.value) { entry.key } }

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

    fun subscribe(store: MutableStore) {
        store.forEach { quad ->
            processor.process(DataAddition(quad)).forEach { process(it) }
        }
        store.addListener(listener)
    }

    fun unsubscribe(store: MutableStore) {
        store.removeListener(listener)
    }

    fun add(quad: Quad) {
        processor.process(DataAddition(quad)).forEach { process(it) }
    }

    fun remove(quad: Quad) {
        processor.process(DataDeletion(quad)).forEach { process(it) }
    }

    private fun process(change: IncrementalQuery.ResultChange<Bindings>) {
        when (val mapped = query.process(change)) {
            is IncrementalQuery.ResultChange.New<*> -> {
                _results.replace(mapped.value) { current -> (current ?: 0) + 1 }
            }
            is IncrementalQuery.ResultChange.Removed<*> -> {
                _results.replace(mapped.value) { current ->
                    when (current) {
                        null, 0 -> throw IllegalStateException("Could not remove ${mapped.value} from the result list as it did not exist!")
                        else -> current - 1
                    }
                }
            }
        }
    }

}
