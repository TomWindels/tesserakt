package dev.tesserakt.benchmarking

import dev.tesserakt.interop.rdfjs.n3.N3Store
import dev.tesserakt.interop.rdfjs.n3.N3Term
import dev.tesserakt.interop.rdfjs.toN3Triple
import dev.tesserakt.sparql.benchmark.replay.SnapshotStore
import kotlinx.coroutines.await


class ComunicaReference(private val query: String) : Reference() {

    private val engine = ComunicaQueryEngine()
    private val store = N3Store()
    private val settings: dynamic = Any()
        .apply { asDynamic().sources = arrayOf(store) }

    private var previous = emptyArray<ComunicaBinding>()
    private var current = emptyArray<ComunicaBinding>()
    private var checksum = 0

    override fun prepare(diff: SnapshotStore.Diff) {
        diff.deletions.forEach {
            store.delete(it.toN3Triple())
        }
        diff.insertions.forEach {
            store.add(it.toN3Triple())
        }
    }

    override suspend fun eval() {
        val query = engine.query(query, settings).await()
        val results = query.toArray().await()
        results.forEach { binding ->
            js("Array")
                .from(this)
                .unsafeCast<Array<Array<dynamic>>>()
                // [ [ name, term | undefined ], ... ]
                .forEach { (_, term) ->
                    val term = term.unsafeCast<N3Term>()
                    checksum += term.checksumValue
                }
        }
        current = results
    }

    override fun finish(): Output {
        val result = compare(current.toList(), previous.toList(), checksum = checksum)
        checksum = 0
        previous = current
        return result
    }

}

private val N3Term.checksumValue: Int
    get() = when (termType) {
        "NamedNode", "Literal", "BlankNode" -> value.length

        "Variable" -> throw IllegalArgumentException("Term `$this` is not supported as a quad term!")

        else -> throw IllegalArgumentException("Unknown term type `$termType`!")
    }
