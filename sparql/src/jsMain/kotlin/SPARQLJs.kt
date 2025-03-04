import dev.tesserakt.sparql.Compiler
import dev.tesserakt.sparql.runtime.common.types.Bindings
import dev.tesserakt.sparql.runtime.incremental.evaluation.OngoingQueryEvaluation
import dev.tesserakt.sparql.runtime.incremental.evaluation.query
import dev.tesserakt.sparql.runtime.incremental.query.IncrementalSelectQuery
import dev.tesserakt.util.jsExpect
import dev.tesserakt.util.mapToArray

@OptIn(ExperimentalJsExport::class)
@JsExport
@JsName("SPARQL")
object SPARQLJs {

//    @JsName("Bindings")
//    class BindingsJs internal constructor(
//        private val value: Map<String, QuadJs.TermJs>
//    ): Map<String, QuadJs.TermJs> by value {
//
//        internal companion object {
//
//            internal fun from(value: Bindings) = BindingsJs(value = value.mapValues { it.value.toJsTerm() })
//
//        }
//
//    }

    @JsName("SelectQuery")
    class SelectQueryJs internal constructor(private val query: IncrementalSelectQuery) {

        fun query(store: MutableStoreJs? = undefined): SelectQueryEvaluationJs {
            return SelectQueryEvaluationJs(store.jsExpect().unwrap().query(query))
        }

    }

    @JsName("SelectQueryEvaluation")
    class SelectQueryEvaluationJs internal constructor(private val evaluation: OngoingQueryEvaluation<Bindings>) {

        val bindings: Array<Any>
            get() = evaluation.results.mapToArray { it.toDynamic() }

    }

    fun Select(input: String? = undefined) = SelectQueryJs(Compiler.Default.compile(input.jsExpect()) as IncrementalSelectQuery)

    private fun Bindings.toDynamic(): Any {
        val result: dynamic = Any()
        forEach { (binding, value) ->
            result[binding] = value
        }
        return result as Any
    }

}
