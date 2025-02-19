package dev.tesserakt.sparql.benchmark.replay

import dev.tesserakt.rdf.dsl.insert
import dev.tesserakt.rdf.ontology.RDF
import dev.tesserakt.rdf.ontology.XSD
import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.rdf.types.Quad.Companion.asLiteralTerm
import dev.tesserakt.rdf.types.Store

class ReplayBenchmark(
    private val identifier: Quad.NamedTerm,
    val store: SnapshotStore,
    val queries: List<String>
) {

    fun interface Evaluator {
        fun evaluate(current: Store, diff: SnapshotStore.Diff)
    }

    init {
        check(queries.isNotEmpty())
    }

    /**
     * Evaluates every version of this benchmark's [store], returning its current entire version and the diff compared
     *  to the previous version.
     */
    inline fun eval(evaluator: Evaluator) {
        val snapshots = store.snapshots.iterator()
        val diffs = store.diffs.iterator()
        while (snapshots.hasNext()) {
            evaluator.evaluate(snapshots.next(), diffs.next())
        }
        // sanity check
        check(!diffs.hasNext())
    }

    fun toStore(target: Store = Store()): Store = target.insert {
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

        private fun extractBenchmarkIdentifierOrBail(term: Quad.Term): Quad.NamedTerm {
            return term as? Quad.NamedTerm ?: throw IllegalStateException("$term is not a valid benchmark identifier!")
        }

        private fun extractQueryOrBail(term: Quad.Term): String {
            check(term is Quad.Literal && term.type == XSD.string)
            return term.value
        }

    }

}
