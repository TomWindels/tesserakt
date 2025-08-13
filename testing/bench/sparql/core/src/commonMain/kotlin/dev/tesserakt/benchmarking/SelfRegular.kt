package dev.tesserakt.benchmarking

import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.rdf.types.SnapshotStore
import dev.tesserakt.rdf.types.factory.MutableStore
import dev.tesserakt.sparql.Bindings
import dev.tesserakt.sparql.Query
import dev.tesserakt.sparql.query


class SelfRegular(private val query: Query<Bindings>): Evaluator() {

    constructor(query: String): this(Query.Select(query))

    private val store = MutableStore()
    private var previous = emptyList<Bindings>()
    private var current = emptyList<Bindings>()
    private var checksum = 0

    // it's not expected that this will be called more than once
    override suspend fun prepare(diff: SnapshotStore.Diff) {
        store.apply {
            diff.insertions.forEach { add(it) }
            diff.deletions.forEach { remove(it) }
        }
    }

    override suspend fun eval() {
        current = store.query(query)
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
            is Quad.LangString -> value.length
            is Quad.NamedTerm -> value.length
            Quad.DefaultGraph -> 0
        }

}
