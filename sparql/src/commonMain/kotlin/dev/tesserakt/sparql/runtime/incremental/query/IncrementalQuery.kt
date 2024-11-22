package dev.tesserakt.sparql.runtime.incremental.query

import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.sparql.runtime.common.types.Bindings
import dev.tesserakt.sparql.runtime.common.util.Debug
import dev.tesserakt.sparql.runtime.incremental.state.IncrementalBasicGraphPatternState
import dev.tesserakt.sparql.runtime.incremental.types.Query

sealed class IncrementalQuery<ResultType, Q: Query>(
    protected val ast: Q
) {

    companion object {

        fun <RT> Iterable<Quad>.query(query: IncrementalQuery<RT, *>, callback: (RT) -> Unit) {
            Debug.reset()
            val processor = with(query) { Processor(this@query) }
            var bindings = processor.next()
            while (bindings != null) {
                callback(query.process(bindings))
                bindings = processor.next()
            }
            Debug.append(processor.debugInformation())
        }

        fun <RT> Sequence<Quad>.query(query: IncrementalQuery<RT, *>): Sequence<RT> = sequence {
            Debug.reset()
            val processor = with(query) { Processor(this@query.iterator()) }
            var bindings = processor.next()
            while (bindings != null) {
                yield(query.process(bindings))
                bindings = processor.next()
            }
            Debug.append(processor.debugInformation())
        }

        fun <RT> Iterable<Quad>.query(query: IncrementalQuery<RT, *>): List<RT> = buildList {
            Debug.reset()
            val processor = with(query) { Processor(this@query) }
            var bindings = processor.next()
            while (bindings != null) {
                add(query.process(bindings))
                bindings = processor.next()
            }
            Debug.append(processor.debugInformation())
        }

    }

    protected inner class Processor(
        private val iterator: Iterator<Quad>
    ) {

        constructor(source: Iterable<Quad>): this(iterator = source.iterator())

        private val state = IncrementalBasicGraphPatternState(ast = ast.body)
        // pending results that have been yielded since last `iterator.next` call, but not yet
        //  processed through `next()`
        private val pending = ArrayList<Bindings>(10)

        fun next(): Bindings? {
            if (pending.isNotEmpty()) {
                return pending.removeFirst()
            }
            while (iterator.hasNext()) {
                val triple = iterator.next()
                val results = state.insert(triple)
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

        fun debugInformation() = state.debugInformation()

    }

    protected abstract fun process(bindings: Bindings): ResultType

}
