package dev.tesserakt.sparql.runtime.incremental.query

import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.sparql.runtime.common.types.Bindings
import dev.tesserakt.sparql.runtime.incremental.patterns.IncrementalRuleSet
import dev.tesserakt.sparql.runtime.incremental.types.Query

sealed class IncrementalQuery<ResultType, Q: Query>(
    protected val ast: Q
) {

    private val incrementalRuleSet = IncrementalRuleSet.from(ast.body.patterns)

    companion object {

        fun <RT> Iterable<Quad>.query(query: IncrementalQuery<RT, *>, callback: (RT) -> Unit) {
            val processor = with(query) { Processor(this@query) }
            var bindings = processor.next()
            while (bindings != null) {
                callback(query.process(bindings))
                bindings = processor.next()
            }
        }

        fun <RT> Sequence<Quad>.query(query: IncrementalQuery<RT, *>): Sequence<RT> = sequence {
            val processor = with(query) { Processor(this@query.iterator()) }
            var bindings = processor.next()
            while (bindings != null) {
                yield(query.process(bindings))
                bindings = processor.next()
            }
        }

        fun <RT> Iterable<Quad>.query(query: IncrementalQuery<RT, *>): List<RT> = buildList {
            val processor = with(query) { Processor(this@query) }
            var bindings = processor.next()
            while (bindings != null) {
                add(query.process(bindings))
                bindings = processor.next()
            }
        }

    }

    protected inner class Processor(
        private val iterator: Iterator<Quad>
    ) {

        constructor(source: Iterable<Quad>): this(iterator = source.iterator())

        private val state = incrementalRuleSet.State()
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
