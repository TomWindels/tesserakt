package dev.tesserakt.sparql.runtime.incremental.query

import dev.tesserakt.sparql.runtime.common.types.Bindings
import dev.tesserakt.sparql.runtime.core.emptyMapping
import dev.tesserakt.sparql.runtime.incremental.delta.DataDelta
import dev.tesserakt.sparql.runtime.incremental.delta.MappingAddition
import dev.tesserakt.sparql.runtime.incremental.delta.MappingDeletion
import dev.tesserakt.sparql.runtime.incremental.delta.MappingDelta
import dev.tesserakt.sparql.runtime.incremental.query.IncrementalQuery.ResultChange.Companion.into
import dev.tesserakt.sparql.runtime.incremental.state.IncrementalBasicGraphPatternState
import dev.tesserakt.sparql.runtime.incremental.types.Query
import kotlin.jvm.JvmInline

sealed class IncrementalQuery<ResultType, Q: Query>(
    protected val ast: Q
) {

    internal inner class Processor {

        private val state = IncrementalBasicGraphPatternState(ast = ast.body)

        /**
         * Required when setting up the initial state: sets up initial state
         *  combinations (i.e. triple patterns such as "?a <p>* <b>", yielding ?a = <b>)
         */
        fun state(): List<ResultType> {
            return state
                // getting all current results by joining with an empty new mapping
                .join(MappingAddition(value = emptyMapping(), origin = null))
                // mapping them to insertion changes, combining them into the expected return type
                .map { bindings -> this@IncrementalQuery.process(ResultChange.New(bindings.value)).value }
        }

        fun process(data: DataDelta): List<ResultChange<Bindings>> {
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
            internal fun MappingDelta.into() = when (this) {
                is MappingAddition -> New(value.bindings)
                is MappingDeletion -> Removed(value.bindings)
            }
        }

    }

    abstract fun process(change: ResultChange<Bindings>): ResultChange<ResultType>

}
