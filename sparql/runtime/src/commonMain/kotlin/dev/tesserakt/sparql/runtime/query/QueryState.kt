package dev.tesserakt.sparql.runtime.query

import dev.tesserakt.sparql.runtime.compat.Compat
import dev.tesserakt.sparql.runtime.evaluation.*
import dev.tesserakt.sparql.runtime.evaluation.context.QueryContext
import dev.tesserakt.sparql.types.QueryStructure
import kotlin.jvm.JvmInline

sealed class QueryState<ResultType, Q: QueryStructure>(
    protected val ast: Q
) {

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

    protected val context = QueryContext(ast)
    protected val bgpState = BasicGraphPatternState(context, ast = Compat.apply(ast.body))

    abstract val results: Collection<ResultType>

    abstract fun processAndGet(data: DataDelta): List<ResultChange<ResultType>>

    abstract fun process(data: DataDelta)

    fun debugInformation() = bgpState.debugInformation()

}
