package dev.tesserakt.sparql.benchmark.replay

import dev.tesserakt.rdf.dsl.insert
import dev.tesserakt.rdf.ontology.RDF
import dev.tesserakt.rdf.types.MutableStore
import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.rdf.types.Quad.Companion.asLiteralTerm
import dev.tesserakt.rdf.types.SnapshotStore
import dev.tesserakt.rdf.types.Store
import dev.tesserakt.rdf.types.factory.MutableStore

class ReplayBenchmark(
    private val identifier: Quad.NamedTerm,
    val store: SnapshotStore,
    val queries: List<String>
) {

    init {
        check(queries.isNotEmpty())
    }

    /**
     * Evaluates every version of this benchmark's [store], returning its current entire version and the diff compared
     *  to the previous version.
     */
    inline fun eval(block: (current: Store, diff: SnapshotStore.Diff) -> Unit) {
        val snapshots = store.snapshots.iterator()
        val diffs = store.diffs.iterator()
        while (snapshots.hasNext()) {
            block(snapshots.next(), diffs.next())
        }
        // sanity check
        check(!diffs.hasNext())
    }

    fun toStore(target: MutableStore = MutableStore()): MutableStore = target.insert {
        identifier has type being RBO.ReplayBenchmark
        identifier has RBO.usesQuery being multiple(queries.map { it.toCleanedUpQuery().asLiteralTerm() })
        identifier has RBO.usesDataset being store.identifier
        +store.toStore()
    }

    companion object {

        fun from(store: Store): List<ReplayBenchmark> {
            val snapshots = SnapshotStore(store)
            val queries = store
                .filter { quad -> quad.p == RBO.usesQuery && store.contains(Quad(quad.s, RBO.usesDataset, snapshots.identifier)) && store.contains(Quad(quad.s, RDF.type, RBO.ReplayBenchmark)) }
                .groupBy(
                    keySelector = { extractBenchmarkIdentifierOrBail(it.s) },
                    valueTransform = { extractQueryOrBail(it.o) }
                )
            return queries.map { (identifier, queries) ->
                ReplayBenchmark(identifier, snapshots, queries)
            }
        }

        private fun extractBenchmarkIdentifierOrBail(term: Quad.Element): Quad.NamedTerm {
            return term as? Quad.NamedTerm ?: throw IllegalStateException("$term is not a valid benchmark identifier!")
        }

        private fun extractQueryOrBail(term: Quad.Element): String {
            check(term is Quad.SimpleLiteral)
            return term.value
        }

    }

}
