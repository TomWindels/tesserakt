
import dev.tesserakt.interop.rdfjs.n3.N3NamedNode
import dev.tesserakt.interop.rdfjs.n3.N3Store
import dev.tesserakt.interop.rdfjs.toN3Store
import dev.tesserakt.interop.rdfjs.toStore
import dev.tesserakt.interop.rdfjs.toTerm
import dev.tesserakt.rdf.serialization.common.Prefixes.Companion.plus
import dev.tesserakt.rdf.serialization.common.serializer
import dev.tesserakt.rdf.serialization.trig.TriG
import dev.tesserakt.rdf.serialization.trig.usePrettyFormatting
import dev.tesserakt.rdf.serialization.trig.withPrefixes
import dev.tesserakt.rdf.types.Quad.Companion.asNamedTerm
import dev.tesserakt.rdf.types.SnapshotStore
import dev.tesserakt.rdf.types.Store
import dev.tesserakt.rdf.types.factory.IndexedStore
import dev.tesserakt.sparql.benchmark.replay.RBO
import dev.tesserakt.sparql.benchmark.replay.ReplayBenchmark

@OptIn(ExperimentalJsExport::class)
@JsExport
class ReplayBenchmarkBuilder(
    name: N3NamedNode,
    start: N3Store
) {

    private val name = name.toTerm()
    private val snapshotBuilder = SnapshotStore.Builder(start = IndexedStore(start.toStore()))
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
        val keys = js("Object.keys")
        val serializer = serializer(TriG) {
            usePrettyFormatting {
                withPrefixes(keys(prefixes).unsafeCast<Array<String>>().associateWith { prefixes[it] }.plus(RBO))
            }
        }
        val fs = js("require('fs')")
        val flags: dynamic = Any()
        // https://nodejs.org/en/learn/manipulating-files/writing-files-with-nodejs#the-flags-youll-likely-use-are
        flags.flag = "a"
        val buf = StringBuilder(5_000) // aiming for 4096 sector sizes
        val iter = serializer.serialize(buildToStore())
        while (iter.hasNext()) {
            // we don't want to exceed the sector size slightly, so we put the allowed scratch length just below it
            while (iter.hasNext() && buf.length < 3_750) {
                buf.append(iter.next())
            }
            if (buf.isNotEmpty()) {
                fs.writeFileSync(path, buf.toString(), flags)
                buf.clear()
            }
        }
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
