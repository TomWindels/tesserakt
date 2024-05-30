package dev.tesserakt.sparql.runtime.incremental.validation

import dev.tesserakt.sparql.runtime.incremental.types.Query
import kotlin.reflect.KClass

abstract class Validator<Q: Query>(private val clazz: KClass<Q>) {

    @Suppress("UNCHECKED_CAST")
    fun validate(ast: Query): Boolean = !clazz.isInstance(ast) || _validate(ast as Q)

    protected abstract fun _validate(query: Q): Boolean

    companion object {

        fun Iterable<Validator<*>>.validate(ast: Query) = forEach { validator ->
            if (!validator.validate(ast)) {
                // TODO: improve exception
                throw IllegalStateException("Validator `${validator::class.simpleName}` failed!")
            }
        }

    }

}
