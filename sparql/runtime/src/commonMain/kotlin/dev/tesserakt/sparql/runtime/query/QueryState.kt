package dev.tesserakt.sparql.runtime.query

import dev.tesserakt.sparql.runtime.compat.Compat
import dev.tesserakt.sparql.runtime.evaluation.*
import dev.tesserakt.sparql.runtime.evaluation.context.QueryContext
import dev.tesserakt.sparql.runtime.evaluation.mapping.Mapping
import dev.tesserakt.sparql.types.QueryStructure
import kotlin.jvm.JvmInline

sealed class QueryState<ResultType, Q: QueryStructure>(
    protected val ast: Q
) {

    sealed interface ResultChange<out T> {

        val value: T

        @JvmInline
        value class New<T>(override val value: T): ResultChange<T>
        @JvmInline
        value class Removed<T>(override val value: T): ResultChange<T>

        companion object {
            inline fun Mapping.into(context: QueryContext) = BindingsImpl(context, this)

            inline fun MappingDelta.asResultChange() = when (this) {
                is MappingAddition -> New(value)
                is MappingDeletion -> Removed(value)
            }

            inline fun MappingDelta.asResultChange(context: QueryContext) = when (this) {
                is MappingAddition -> New(value.into(context))
                is MappingDeletion -> Removed(value.into(context))
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
