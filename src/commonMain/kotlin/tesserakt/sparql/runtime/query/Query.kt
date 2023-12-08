package tesserakt.sparql.runtime.query

import tesserakt.rdf.types.TripleSource
import tesserakt.sparql.compiler.types.Patterns
import tesserakt.sparql.compiler.types.QueryAST
import kotlin.jvm.JvmInline

sealed class Query<ResultType, AST: QueryAST>(
    protected val ast: AST
) {

    private val queryPlan = QueryPlan(ast.body.patterns)

    companion object {

        fun <RT> TripleSource.query(query: Query<RT, *>, callback: (RT) -> Unit) {
            val processor = with(query) { Processor(this@query) }
            var bindings = processor.next()
            while (bindings != null) {
                callback(query.process(bindings))
                bindings = processor.next()
            }
        }

        fun <RT> TripleSource.queryAsSequence(query: Query<RT, *>): Sequence<RT> = sequence {
            val processor = with(query) { Processor(this@queryAsSequence) }
            var bindings = processor.next()
            while (bindings != null) {
                yield(query.process(bindings))
                bindings = processor.next()
            }
        }

        fun <RT> TripleSource.queryAsList(query: Query<RT, *>): List<RT> = buildList {
            val processor = with(query) { Processor(this@queryAsList) }
            var bindings = processor.next()
            while (bindings != null) {
                add(query.process(bindings))
                bindings = processor.next()
            }
        }

    }

    @JvmInline
    protected value class Bindings(val bindings: Map<String, Any>)

    private class QueryPlan(from: Patterns) {

    }

    inner class Processor(
        private val source: TripleSource
    ) {

        fun next(): Bindings? {

        }

    }

    protected abstract fun process(bindings: Bindings): ResultType

}
