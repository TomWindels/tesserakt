package dev.tesserakt.benchmarking

import dev.tesserakt.rdf.types.MutableStore
import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.sparql.Bindings
import dev.tesserakt.sparql.Query
import dev.tesserakt.sparql.benchmark.replay.SnapshotStore
import dev.tesserakt.sparql.query


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

private val Quad.Term.checksumLength: Int
    get() = when (this) {
        is Quad.BlankTerm -> id.toString().length
        is Quad.Literal -> value.length
        is Quad.NamedTerm -> value.length
    }
