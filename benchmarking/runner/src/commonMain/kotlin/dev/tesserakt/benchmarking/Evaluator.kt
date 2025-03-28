package dev.tesserakt.benchmarking

import dev.tesserakt.rdf.types.MutableStore
import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.sparql.Bindings
import dev.tesserakt.sparql.Query
import dev.tesserakt.sparql.benchmark.replay.SnapshotStore
import dev.tesserakt.sparql.query

abstract class Evaluator {

    /**
     * An evaluation result, representing the number of [added] and [removed] binding[s] compared to the previous state
     */
    data class Output(
        val added: Int,
        val removed: Int,
        val checksum: Int,
    )

    /**
     * Prepares a diff to be evaluated, w/o actually evaluating it
     */
    abstract fun prepare(diff: SnapshotStore.Diff)

    /**
     * Evaluates the diff; this is the method which execution time matters!
     */
    abstract fun eval()

    /**
     * Evaluates the result difference after applying the diff
     */
    abstract fun finish(): Output

    class Self(query: Query<Bindings>): Evaluator() {

        constructor(query: String): this(Query.Select(query))

        private val store = MutableStore()
        private val eval = store.query(query)
        private lateinit var diff: SnapshotStore.Diff
        private var previous = emptyList<Bindings>()
        private var current = emptyList<Bindings>()
        private var checksum = 0

        override fun prepare(diff: SnapshotStore.Diff) {
            this.diff = diff
        }

        override fun eval() {
            store.apply {
                diff.deletions.forEach { remove(it) }
                diff.insertions.forEach { add(it) }
            }
            current = eval.results.toList()
            checksum = current.sumOf { it.sumOf { it.second.checksumLength } }
        }

        override fun finish(): Output {
            val result = compare(current, previous, checksum = checksum)
            previous = current
            return result
        }

    }

    companion object {

        fun self(query: String) = Self(query)

        fun reference(query: String) = Reference(query)

    }

}

expect class Reference(query: String): Evaluator

private val Quad.Term.checksumLength: Int
    get() = when (this) {
        is Quad.BlankTerm -> id.toString().length
        is Quad.Literal -> value.length
        is Quad.NamedTerm -> value.length
    }
