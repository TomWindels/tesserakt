
import dev.tesserakt.interop.rdfjs.n3.N3NamedNode
import dev.tesserakt.interop.rdfjs.n3.N3Store
import dev.tesserakt.interop.rdfjs.toN3Store
import dev.tesserakt.interop.rdfjs.toStore
import dev.tesserakt.interop.rdfjs.toTerm
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

    fun build(): N3Store {
        return builder.build(name.toTerm()).toStore().toN3Store()
    }

}
