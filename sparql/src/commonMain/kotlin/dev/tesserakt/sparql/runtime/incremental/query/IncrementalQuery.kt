package dev.tesserakt.sparql.runtime.incremental.query

import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.sparql.runtime.common.types.Bindings
import dev.tesserakt.sparql.runtime.common.util.Debug
import dev.tesserakt.sparql.runtime.incremental.delta.Delta
import dev.tesserakt.sparql.runtime.incremental.delta.addition
import dev.tesserakt.sparql.runtime.incremental.query.IncrementalQuery.ResultChange.Companion.into
import dev.tesserakt.sparql.runtime.incremental.state.IncrementalBasicGraphPatternState
import dev.tesserakt.sparql.runtime.incremental.types.Query
import kotlin.jvm.JvmInline

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
        private val pending = ArrayList<ResultChange>(10)

        fun next(): ResultChange? {
            if (pending.isNotEmpty()) {
                return pending.removeFirst()
            }
            while (iterator.hasNext()) {
                val triple = iterator.next()
                val results = state.insert(addition(triple))
                return when (results.size) {
                    // continuing looping
                    0 -> continue
                    // only one result, so yielding that and leaving the pending list alone
                    1 -> results.first().into()
                    // yielding the first one, adding all other ones to pending
                    else -> results.first().into().also {
                        for (r in 1 ..< results.size) { pending.add(results[r].into()) }
                    }
                }
            }
            return null
        }

        fun debugInformation() = state.debugInformation()

    }

    sealed interface ResultChange {

        val bindings: Bindings

        @JvmInline
        value class New(override val bindings: Bindings): ResultChange
        @JvmInline
        value class Removed(override val bindings: Bindings): ResultChange

        companion object {
            fun Delta.Bindings.into() = when (this) {
                is Delta.BindingsAddition -> New(value)
            }
        }

    }

    protected abstract fun process(change: ResultChange): ResultType

}
