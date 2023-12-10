package tesserakt.sparql.runtime.query

import tesserakt.rdf.types.Triple
import tesserakt.sparql.compiler.types.QueryAST
import tesserakt.sparql.runtime.patterns.PatternSearch
import tesserakt.sparql.runtime.patterns.RuleSet
import tesserakt.sparql.runtime.types.Bindings

sealed class Query<ResultType, AST: QueryAST>(
    protected val ast: AST
) {

    private val ruleSet = RuleSet.from(ast.body.patterns)

    companion object {

        fun <RT> Iterable<Triple>.query(query: Query<RT, *>, callback: (RT) -> Unit) {
            val processor = with(query) { Processor(this@query) }
            var bindings = processor.next()
            while (bindings != null) {
                callback(query.process(bindings))
                bindings = processor.next()
            }
        }

        fun <RT> Iterable<Triple>.queryAsSequence(query: Query<RT, *>): Sequence<RT> = sequence {
            val processor = with(query) { Processor(this@queryAsSequence) }
            var bindings = processor.next()
            while (bindings != null) {
                yield(query.process(bindings))
                bindings = processor.next()
            }
        }

        fun <RT> Iterable<Triple>.queryAsList(query: Query<RT, *>): List<RT> = buildList {
            val processor = with(query) { Processor(this@queryAsList) }
            var bindings = processor.next()
            while (bindings != null) {
                add(query.process(bindings))
                bindings = processor.next()
            }
        }

    }

    protected inner class Processor(
        source: Iterable<Triple>
    ) {

        private val search = PatternSearch(ruleSet)
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
                val results = search.process(triple)
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
