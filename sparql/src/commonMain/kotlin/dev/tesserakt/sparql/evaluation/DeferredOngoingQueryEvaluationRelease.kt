package dev.tesserakt.sparql.evaluation

import dev.tesserakt.rdf.types.ObservableStore
import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.sparql.runtime.evaluation.BindingsImpl
import dev.tesserakt.sparql.runtime.evaluation.DataAddition
import dev.tesserakt.sparql.runtime.evaluation.DataDeletion
import dev.tesserakt.sparql.runtime.evaluation.DataDelta
import dev.tesserakt.sparql.runtime.query.QueryState
import dev.tesserakt.util.replace
import kotlin.jvm.JvmInline


class DeferredOngoingQueryEvaluationRelease<RT>(private val query: QueryState<RT, *>): DeferredOngoingQueryEvaluation<RT> {

    private sealed interface Change {
        @JvmInline
        value class Addition(val quad: Quad) : Change

        @JvmInline
        value class Deletion(val quad: Quad) : Change

        fun into(): DataDelta = when (this) {
            is Addition -> DataAddition(quad)
            is Deletion -> DataDeletion(quad)
        }

        fun anti(): Change = when (this) {
            is Addition -> Deletion(quad)
            is Deletion -> Addition(quad)
        }
    }

    // a possibly out-of-date state of the ongoing query
    private val _results = mutableMapOf<RT, Int>()

    override val results: List<RT>
        get() {
            // ensuring the _results are up-to-date
            update()
            return _results.flatMap { entry -> List(entry.value) { entry.key } }
        }

    private val processor = query.Processor()
    // a set, as duplicate insertions / deletions are not possible, and opposite changes can be found efficiently
    private val queue = mutableSetOf<Change>()

    private val listener = object: ObservableStore.Listener {
        override fun onQuadAdded(quad: Quad) {
            process(Change.Addition(quad))
        }

        override fun onQuadRemoved(quad: Quad) {
            process(Change.Deletion(quad))
        }
    }

    init {
        // setting the query state as the current results
        processor.state().forEach {
            _results[it] = 1
        }
    }

    override fun subscribe(store: ObservableStore) {
        // adding the existing store contents to the queue
        store.forEach {
            process(Change.Addition(it))
        }
        // we can now queue up additional changes
        store.addListener(listener)
    }

    override fun unsubscribe(store: ObservableStore) {
        store.removeListener(listener)
    }

    override fun debugInformation(): String {
        return processor.debugInformation()
    }

    /**
     * Evaluates all changes since the last evaluation. This is automatically called whenever [results] are being read.
     */
    fun update() {
        val iter = queue.iterator()
        while (iter.hasNext()) {
            processor.process(iter.next().into()).forEach(::process)
            iter.remove()
        }
    }

    private fun process(change: Change) {
        // if the other operation is already queued, we can remove it instead of inserting the new change
        if (queue.remove(change.anti())) {
            return
        }
        queue.add(change)
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
