import dev.tesserakt.interop.rdfjs.n3.N3Store
import dev.tesserakt.interop.rdfjs.toN3Store
import kotlin.js.collections.JsSet
import kotlin.js.collections.toSet

@OptIn(ExperimentalJsExport::class)
@JsExport
object RDFJS {

    fun fromStore(store: MutableStoreJs): N3Store {
        return store.unwrap().toN3Store()
    }

    @OptIn(ExperimentalJsCollectionsApi::class)
    fun fromSet(store: JsSet<QuadJs>): N3Store {
        return store.toSet().map { it.unwrap() }.toN3Store()
    }

    fun fromArray(store: Array<QuadJs>): N3Store {
        return store.map { it.unwrap() }.toN3Store()
    }

}
