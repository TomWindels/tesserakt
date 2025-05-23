
import dev.tesserakt.sparql.Bindings
import dev.tesserakt.sparql.Compiler
import dev.tesserakt.sparql.OngoingQueryEvaluation
import dev.tesserakt.sparql.query
import dev.tesserakt.sparql.runtime.createState
import dev.tesserakt.sparql.runtime.evaluation.BindingsImpl
import dev.tesserakt.sparql.runtime.query.SelectQueryState
import dev.tesserakt.util.jsExpect
import dev.tesserakt.util.mapToArray
import kotlin.js.collections.JsMap

@OptIn(ExperimentalJsExport::class)
@JsExport
@JsName("SPARQL")
object SPARQLJs {

    @OptIn(ExperimentalJsCollectionsApi::class)
    @JsName("Bindings")
    class BindingsJs internal constructor(private val src: MutableMap<String, QuadJs.TermJs>): JsMap<String, QuadJs.TermJs>() {
        internal constructor(original: Bindings): this(src = original.associateTo(mutableMapOf()) { it.first to it.second.toJsTerm() })
    }

    @JsName("SelectQuery")
    class SelectQueryJs internal constructor(internal val query: SelectQueryState)

    @JsName("SelectQueryEvaluation")
    class SelectQueryEvaluationJs internal constructor(private val evaluation: OngoingQueryEvaluation<BindingsImpl>) {

        @OptIn(ExperimentalJsCollectionsApi::class)
        val bindings: Array<JsMap<String, QuadJs.TermJs>>
            get() = evaluation.results.mapToArray { it.associateTo(mutableMapOf()) { it.first to it.second.toJsTerm() }.asJsMapView() }

    }

    fun Select(input: String? = undefined) = SelectQueryJs(Compiler().compile(input.jsExpect()).structure.createState() as SelectQueryState)

    fun query(
        query: SelectQueryJs? = undefined,
        store: MutableStoreJs? = undefined
    ): SelectQueryEvaluationJs {
        return SelectQueryEvaluationJs(store.jsExpect().unwrap().query(query.jsExpect().query))
    }

//    private fun Bindings.toDynamic(): Any {
//        val result: dynamic = Any()
//        forEach { (binding, value) ->
//            result[binding] = value.toJsTerm()
//        }
//        return result as Any
//    }

}
