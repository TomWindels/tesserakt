
import dev.tesserakt.interop.rdfjs.n3.N3NamedNode
import dev.tesserakt.interop.rdfjs.n3.N3Store
import dev.tesserakt.interop.rdfjs.toN3Store
import dev.tesserakt.interop.rdfjs.toStore
import dev.tesserakt.interop.rdfjs.toTerm
import dev.tesserakt.rdf.trig.serialization.TriGSerializer
import dev.tesserakt.sparql.benchmark.replay.SnapshotStore

@OptIn(ExperimentalJsExport::class)
@JsExport
class SnapshotStoreBuilder(
    private val name: N3NamedNode,
    start: N3Store
) {

    private val builder = SnapshotStore.Builder(start = start.toStore())

    fun add(store: N3Store): SnapshotStoreBuilder {
        builder.addSnapshot(store.toStore())
        return this
    }

    fun buildToStore(): N3Store {
        return builder.build(name.toTerm()).toStore().toN3Store()
    }

    fun buildToFile(path: String = "./${name.value}.ttl", prefixes: dynamic) {
        val fs = js("require('fs')")
        val keys = js("Object.keys")
        val flags: dynamic = Any()
        // https://nodejs.org/en/learn/manipulating-files/writing-files-with-nodejs#the-flags-youll-likely-use-are
        flags.flag = "a"
        TriGSerializer.serialize(
            store = builder.build(name.toTerm()).toStore(),
            prefixes = keys(prefixes).unsafeCast<Array<String>>().associateWith { prefixes[it] },
            callback = { content -> fs.writeFileSync(path, content, flags); Unit }
        )
    }

}
