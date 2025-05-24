package dev.tesserakt.sparql.runtime.query

import dev.tesserakt.sparql.runtime.compat.Compat
import dev.tesserakt.sparql.runtime.evaluation.*
import dev.tesserakt.sparql.runtime.evaluation.context.QueryContext
import dev.tesserakt.sparql.runtime.query.QueryState.ResultChange.Companion.into
import dev.tesserakt.sparql.types.QueryStructure
import kotlin.jvm.JvmInline

sealed class QueryState<ResultType, Q: QueryStructure>(
    protected val ast: Q
) {

    inner class Processor {

        val context = QueryContext(ast)
        private val state = BasicGraphPatternState(context, ast = Compat.apply(ast.body))

        /**
         * Required when setting up the initial state: sets up initial state
         *  combinations (i.e. triple patterns such as "?a <p>* <b>", yielding ?a = <b>)
         */
        fun state(): List<ResultType> {
            return state
                // getting all current results by joining with an empty new mapping
                .join(
                    MappingAddition(
                        value = state.context.emptyMapping(),
                        origin = null
                    )
                )
                // mapping them to insertion changes, combining them into the expected return type
                .map { bindings -> this@QueryState.process(ResultChange.New(BindingsImpl(context, bindings.value))).value }
        }

        fun process(data: DataDelta): List<ResultChange<BindingsImpl>> {
            return state.insert(data).map { it.into(context) }
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
            fun MappingDelta.into(context: QueryContext) = when (this) {
                is MappingAddition -> New(BindingsImpl(context, value))
                is MappingDeletion -> Removed(BindingsImpl(context, value))
            }
        }

    }

    abstract fun process(change: ResultChange<BindingsImpl>): ResultChange<ResultType>

}
