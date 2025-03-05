import dev.tesserakt.interop.rdfjs.n3.N3Store
import dev.tesserakt.interop.rdfjs.toN3Store

@OptIn(ExperimentalJsExport::class)
@JsExport
fun toRDFJSStore(store: MutableStoreJs): N3Store {
    return store.unwrap().toN3Store()
}
