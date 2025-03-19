package dev.tesserakt.benchmarking

import dev.tesserakt.rdf.types.MutableStore
import dev.tesserakt.sparql.Query
import dev.tesserakt.sparql.benchmark.replay.SnapshotStore
import dev.tesserakt.sparql.query

abstract class Evaluator {

    /**
     * Prepares a diff to be evaluated, w/o actually evaluating it
     */
    abstract fun prepare(diff: SnapshotStore.Diff)

    /**
     * Evaluates the diff, this is the method which execution time matters!
     */
    abstract fun apply()

    class Self(query: Query<*>): Evaluator() {

        constructor(query: String): this(Query.Select(query))

        private val store = MutableStore()
        private val eval = store.query(query)
        private lateinit var diff: SnapshotStore.Diff

        override fun prepare(diff: SnapshotStore.Diff) {
            this.diff = diff
        }

        override fun apply() {
            store.apply {
                diff.insertions.forEach { add(it) }
                diff.deletions.forEach { remove(it) }
            }
        }

    }

    companion object {

        fun self(query: String) = Self(query)

        fun reference(query: String) = Reference(query)

    }

}

expect class Reference(query: String): Evaluator
