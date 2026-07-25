import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.rdf.types.Store
import dev.tesserakt.sparql.Bindings
import dev.tesserakt.sparql.Query
import dev.tesserakt.sparql.query
import dev.tesserakt.util.mapToArray
import kotlin.js.Promise

@OptIn(ExperimentalJsExport::class)
@JsExport
class BindingsStream internal constructor(
    query: Query<Bindings>,
    store: Store,
) : ReadableJs(
    // we push binding objects, so we set that option
    opts = run {
        val options: dynamic = Any()
        options.objectMode = true
        options
    },
) {

    private val df = DataFactoryJs()
    private val bf = BindingsFactoryJs(df)

    // number of consumed items, used in the readable stream API
    private var i = 0

    @OptIn(ExperimentalWasmJsInterop::class)
    private val results = store.query(query).map { bindings ->
        val arg = bindings.toList().mapToArray { (name, term) ->
            val arr = JsArray<JsAny?>(2) { null }
            arr[0] = df.variable(name)
            arr[1] = when (term) {
                is Quad.BlankTerm -> df.blankNode(term.id.toString())
                Quad.DefaultGraph -> df.defaultGraph()
                is Quad.NamedTerm -> df.namedNode(term.value)
                is Quad.LangString -> df.literal(term.value, term.language)
                is Quad.SimpleLiteral -> df.literal(term.value, null)
                is Quad.TypedLiteral -> df.literal(term.value, df.namedNode(term.type.value))
            }
            @Suppress("UNCHECKED_CAST")
            arr as JsArray<JsAny>
        }
        bf.bindings(arg)
    }

    @OptIn(ExperimentalWasmJsInterop::class)
    fun toArray(): Promise<JsArray<BindingsJs>> {
        // we only return those not yet consumed, so starting from `this.i`
        return Promise.resolve(JsArray(results.size - i) { j -> results[j + i] })
    }

    @OptIn(ExperimentalWasmJsInterop::class)
    override fun read() {
        if (this.i >= results.size) {
            // EOF
            this.push(null)
            return
        }
        this.push(results[i++])
    }

}
