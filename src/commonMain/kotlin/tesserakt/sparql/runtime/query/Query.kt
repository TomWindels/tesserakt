package tesserakt.sparql.runtime.query

import tesserakt.rdf.types.TripleSource
import tesserakt.sparql.compiler.types.QueryAST
import tesserakt.sparql.runtime.types.Bindings

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

    protected inner class Processor(
        source: TripleSource
    ) {

        private val queryState = queryPlan.newState()
        private val iterator = source.iterator()
        // pending results that have been yielded since last `iterator.next` call, but not yet
        //  processed through `next()`
        private val pending = mutableListOf<Bindings>()

        fun next(): Bindings? {
            if (pending.isNotEmpty()) {
                return pending.removeFirst()
            }
            while (iterator.hasNext()) {
                val triple = iterator.next()
                val results = queryPlan.process(queryState, triple)
                return when (results.size) {
                    // continuing looping
                    0 -> continue
                    // only one result, so yielding that and leaving the pending list alone
                    1 -> results.first()
                    // yielding the first one, adding all other ones to pending
                    else -> results.removeFirst().also { pending.addAll(results) }
                }
            }
            return null
        }

    }

    protected abstract fun process(bindings: Bindings): ResultType

}
