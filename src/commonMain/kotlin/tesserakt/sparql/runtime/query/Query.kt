package tesserakt.sparql.runtime.query

import tesserakt.rdf.types.Triple
import tesserakt.sparql.compiler.types.QueryAST
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

        fun <RT> Sequence<Triple>.query(query: Query<RT, *>): Sequence<RT> = sequence {
            val processor = with(query) { Processor(this@query.iterator()) }
            var bindings = processor.next()
            while (bindings != null) {
                yield(query.process(bindings))
                bindings = processor.next()
            }
        }

        fun <RT> Iterable<Triple>.query(query: Query<RT, *>): List<RT> = buildList {
            val processor = with(query) { Processor(this@query) }
            var bindings = processor.next()
            while (bindings != null) {
                add(query.process(bindings))
                bindings = processor.next()
            }
        }

    }

    protected inner class Processor(
        private val iterator: Iterator<Triple>
    ) {

        constructor(source: Iterable<Triple>): this(iterator = source.iterator())

        private val state = ruleSet.State()
        // pending results that have been yielded since last `iterator.next` call, but not yet
        //  processed through `next()`
        private val pending = ArrayList<Bindings>(10)

        fun next(): Bindings? {
            if (pending.isNotEmpty()) {
                return pending.removeFirst()
            }
            while (iterator.hasNext()) {
                val triple = iterator.next()
                val results = state.process(triple)
                return when (results.size) {
                    // continuing looping
                    0 -> continue
                    // only one result, so yielding that and leaving the pending list alone
                    1 -> results.first()
                    // yielding the first one, adding all other ones to pending
                    else -> results.first().also {
                        for (r in 1 ..< results.size) { pending.add(results[r]) }
                    }
                }
            }
            return null
        }

    }

    protected abstract fun process(bindings: Bindings): ResultType

}
