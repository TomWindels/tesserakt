package dev.tesserakt.benchmarking

import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.rdf.types.SnapshotStore
import dev.tesserakt.rdf.types.factory.ObservableStore
import dev.tesserakt.sparql.Bindings
import dev.tesserakt.sparql.Query
import dev.tesserakt.sparql.query


class SelfIncremental(query: Query<Bindings>): Evaluator() {

    constructor(query: String): this(Query.Select(query))

    private val store = ObservableStore()
    private val eval = store.query(query)
    private lateinit var diff: SnapshotStore.Diff
    private var previous = emptyList<Bindings>()
    private var current = emptyList<Bindings>()
    private var checksum = 0

    override suspend fun prepare(diff: SnapshotStore.Diff) {
        this.diff = diff
    }

    override suspend fun eval() {
        store.apply {
            diff.insertions.forEach { add(it) }
            diff.deletions.forEach { remove(it) }
        }
        current = eval.results.toList()
        checksum = current.sumOf { it.sumOf { it.second.checksumLength } }
    }

    override fun finish(): Output {
        val result = compare(current, previous, checksum = checksum)
        previous = current
        return result
    }

    private val Quad.Element.checksumLength: Int
        get() = when (this) {
            is Quad.BlankTerm -> 1
            is Quad.Literal -> value.length
            is Quad.NamedTerm -> value.length
            Quad.DefaultGraph -> 0
        }

}
