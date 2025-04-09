
import dev.tesserakt.interop.rdfjs.n3.N3NamedNode
import dev.tesserakt.interop.rdfjs.n3.N3Store
import dev.tesserakt.interop.rdfjs.toN3Store
import dev.tesserakt.interop.rdfjs.toStore
import dev.tesserakt.interop.rdfjs.toTerm
import dev.tesserakt.rdf.serialization.common.Prefixes.Companion.plus
import dev.tesserakt.rdf.trig.serialization.TriGSerializer
import dev.tesserakt.rdf.types.Quad.Companion.asNamedTerm
import dev.tesserakt.rdf.types.SnapshotStore
import dev.tesserakt.rdf.types.Store
import dev.tesserakt.sparql.benchmark.replay.RBO
import dev.tesserakt.sparql.benchmark.replay.ReplayBenchmark

@OptIn(ExperimentalJsExport::class)
@JsExport
class ReplayBenchmarkBuilder(
    name: N3NamedNode,
    start: N3Store
) {

    private val name = name.toTerm()
    private val snapshotBuilder = SnapshotStore.Builder(start = start.toStore())
    private val datasetName = (name.value + "_dataset").asNamedTerm()
    private val queries = mutableListOf<String>()

    fun addQuery(query: String): ReplayBenchmarkBuilder {
        queries.add(query)
        return this
    }

    fun addSnapshot(store: N3Store): ReplayBenchmarkBuilder {
        snapshotBuilder.addSnapshot(store.toStore())
        return this
    }

    fun build(): N3Store {
        return buildToStore().toN3Store()
    }

    fun buildToFile(path: String = "./${name.value}.ttl", prefixes: dynamic) {
        val fs = js("require('fs')")
        val keys = js("Object.keys")
        val flags: dynamic = Any()
        // https://nodejs.org/en/learn/manipulating-files/writing-files-with-nodejs#the-flags-youll-likely-use-are
        flags.flag = "a"
        TriGSerializer.serialize(
            data = buildToStore(),
            prefixes = keys(prefixes).unsafeCast<Array<String>>().associateWith { prefixes[it] }.plus(RBO),
            callback = { content -> fs.writeFileSync(path, content, flags); Unit }
        )
    }

    private fun buildToStore(): Store {
        val snapshotStore = snapshotBuilder.build(datasetName)
        require(queries.isNotEmpty()) {
            "No queries provided for this benchmark! Did you forget to use `builder.addQuery()`?"
        }
        val benchmark = ReplayBenchmark(identifier = name, store = snapshotStore, queries = queries)
        return benchmark.toStore()
    }

}
