package dev.tesserakt.sparql.runtime.incremental.query

import dev.tesserakt.sparql.runtime.common.types.Bindings
import dev.tesserakt.sparql.runtime.incremental.delta.Delta
import dev.tesserakt.sparql.runtime.incremental.query.IncrementalQuery.ResultChange.Companion.into
import dev.tesserakt.sparql.runtime.incremental.state.IncrementalBasicGraphPatternState
import dev.tesserakt.sparql.runtime.incremental.types.Query
import kotlin.jvm.JvmInline

sealed class IncrementalQuery<ResultType, Q: Query>(
    protected val ast: Q
) {

    internal inner class Processor {

        private val state = IncrementalBasicGraphPatternState(ast = ast.body)

        fun process(data: Delta.Data): List<ResultChange<Bindings>> {
            return state.insert(data).map { it.into() }
        }

        fun debugInformation() = state.debugInformation()

    }

    sealed interface ResultChange<T> {

        val value: T

        @JvmInline
        value class New<T>(override val value: T): ResultChange<T>
        @JvmInline
        value class Removed<T>(override val value: T): ResultChange<T>

        companion object {
            fun Delta.Bindings.into() = when (this) {
                is Delta.BindingsAddition -> New(value)
                is Delta.BindingsDeletion -> Removed(value)
            }
        }

    }

    abstract fun process(change: ResultChange<Bindings>): ResultChange<ResultType>

}
