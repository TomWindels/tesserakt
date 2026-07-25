
import dev.tesserakt.interop.rdfjs.n3.N3Store
import dev.tesserakt.interop.rdfjs.toStore
import dev.tesserakt.sparql.Query
import kotlin.js.Promise

@OptIn(ExperimentalJsExport::class)
@JsExport
class ComunicaApiEngine {

    fun queryBindings(queryString: String?, opts: dynamic?): Promise<BindingsStream> = runCatching {
        if (queryString !is String) {
            throw Error("The provided query is not a valid string: `${queryString}`")
        }
        val query = Query.Select(queryString)
        if (opts == null) {
            throw Error("No options (and thus data source) have been provided!")
        }
        val sources = when (val sources = opts.sources) {
            is Array<*> -> {
                val sources = sources as Array<*>
                when (sources.size) {
                    0 -> throw Error("The sources array is empty!")
                    1 -> sources.first()
                    else -> {
                        throw Error("Invalid number of sources provided! Only exactly 1 store is supported!")
                    }
                }
            }
            null -> throw Error("No data source has been provided!")
            else -> sources
        }
        val store = when (sources) {
            is StoreJs -> {
                sources.unwrap()
            }
            else -> {
                // assuming it's an `N3Store`-like instance; if we use a method that does not exist, the error message
                //  will be accurate
                sources.unsafeCast<N3Store>().toStore()
            }
        }
        BindingsStream(
            query = query,
            store = store,
        )
    }.fold(
        onSuccess = { Promise.resolve(it) },
        onFailure = { Promise.reject(Error(it.message)) }
    )

}
