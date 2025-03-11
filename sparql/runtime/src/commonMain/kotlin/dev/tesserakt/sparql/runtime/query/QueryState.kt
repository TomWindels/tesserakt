package dev.tesserakt.sparql.runtime.query

import dev.tesserakt.sparql.Bindings
import dev.tesserakt.sparql.ast.CompiledQuery
import dev.tesserakt.sparql.runtime.evaluation.*
import dev.tesserakt.sparql.runtime.query.QueryState.ResultChange.Companion.into
import kotlin.jvm.JvmInline

sealed class QueryState<ResultType, Q: CompiledQuery>(
    protected val ast: Q
) {

    inner class Processor {

        private val state = BasicGraphPatternState(ast = ast.body)

        /**
         * Required when setting up the initial state: sets up initial state
         *  combinations (i.e. triple patterns such as "?a <p>* <b>", yielding ?a = <b>)
         */
        fun state(): List<ResultType> {
            return state
                // getting all current results by joining with an empty new mapping
                .join(
                    MappingAddition(
                        value = emptyMapping(),
                        origin = null
                    )
                )
                // mapping them to insertion changes, combining them into the expected return type
                .map { bindings -> this@QueryState.process(ResultChange.New(bindings.value)).value }
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
            fun MappingDelta.into() = when (this) {
                is MappingAddition -> New(value.bindings)
                is MappingDeletion -> Removed(value.bindings)
            }
        }

    }

    abstract fun process(change: ResultChange<Bindings>): ResultChange<ResultType>

}
