import dev.tesserakt.rdf.types.MutableStore
import dev.tesserakt.util.jsExpect

/**
 * A thin wrapper for the [MutableStore] type. This is not a data class, as the copy method cannot be exposed
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
@JsName("MutableStore")
class MutableStoreJs(quads: Array<QuadJs>? = undefined) {

    internal val store = MutableStore(quads?.map { it.unwrap() } ?: emptyList())

    override fun equals(other: Any?): Boolean {
        if (other !is MutableStoreJs) {
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
        return store.contains(element.unwrap())
    }

    fun insert(quad: QuadJs? = undefined) {
        store.add(quad.jsExpect().unwrap())
    }

    fun insertQuad(s: QuadJs.TermJs? = undefined, p: QuadJs.TermJs? = undefined, o: QuadJs.TermJs? = undefined, g: QuadJs.GraphJs? = undefined) {
        store.add(QuadJs(s, p, o, g ?: QuadJs.graph()).unwrap())
    }

}
