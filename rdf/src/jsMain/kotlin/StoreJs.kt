
import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.rdf.types.factory.ObservableStore
import dev.tesserakt.util.jsExpect
import dev.tesserakt.util.mapToArray

/**
 * A thin wrapper for the [ObservableStore] type.
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
@JsName("Store")
class StoreJs
@OptIn(ExperimentalWasmJsInterop::class)
constructor(quads: JsArray<QuadJs>? = undefined) {

    @OptIn(ExperimentalWasmJsInterop::class)
    internal val store = ObservableStore(quads?.map { it.value } ?: emptyList())

    override fun equals(other: Any?): Boolean {
        if (other !is StoreJs) {
            return false
        }
        return store == other.store
    }

    override fun hashCode(): Int {
        return store.hashCode()
    }

    override fun toString(): String {
        return store.toString()
    }

    val size: Int
        get() = store.size

    fun isEmpty(): Boolean {
        return store.isEmpty()
    }

    fun contains(element: QuadJs): Boolean {
        return store.contains(element.value)
    }

    fun insert(quad: QuadJs? = undefined) {
        store.add(quad.jsExpect().value)
    }

    fun remove(quad: QuadJs? = undefined) {
        store.remove(quad.jsExpect().value)
    }

    fun insertQuad(s: TermJs?, p: TermJs?, o: TermJs?, g: GraphTerm? = undefined) {
        store.add(QuadJs(s, p, o, g).value)
    }

    fun toArray(): Array<QuadJs> {
        return store.map { QuadJs(it) }.toTypedArray()
    }

}

// not exported as it contains KT-only types
@OptIn(ExperimentalWasmJsInterop::class)
fun StoreJs(contents: Collection<Quad>) = StoreJs(quads = contents.mapToArray { QuadJs(it) })
