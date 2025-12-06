package dev.tesserakt.sparql.evaluation

import dev.tesserakt.rdf.types.ObservableStore
import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.sparql.runtime.evaluation.DataAddition
import dev.tesserakt.sparql.runtime.evaluation.DataDeletion
import dev.tesserakt.sparql.runtime.evaluation.DataDelta
import dev.tesserakt.sparql.runtime.query.QueryState
import kotlin.jvm.JvmInline


internal class DeferredOngoingQueryEvaluationImpl<RT>(private val query: QueryState<RT, *>): DeferredOngoingQueryEvaluation<RT> {

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

    override val results: Collection<RT>
        get() {
            // ensuring the _results are up-to-date
            update()
            return query.results
        }

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
        return query.debugInformation()
    }

    /**
     * Evaluates all changes since the last evaluation. This is automatically called whenever [results] are being read.
     */
    fun update() {
        val iter = queue.iterator()
        while (iter.hasNext()) {
            query.process(iter.next().into())
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

}
