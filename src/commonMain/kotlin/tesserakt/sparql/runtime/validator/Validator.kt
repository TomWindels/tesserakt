package tesserakt.sparql.runtime.validator

import tesserakt.sparql.runtime.types.QueryASTr
import kotlin.reflect.KClass

abstract class Validator<AST: QueryASTr>(private val clazz: KClass<AST>) {

    @Suppress("UNCHECKED_CAST")
    fun validate(ast: QueryASTr): Boolean = !clazz.isInstance(ast) || _validate(ast as AST)

    protected abstract fun _validate(ast: AST): Boolean

    companion object {

        fun Iterable<Validator<*>>.validate(ast: QueryASTr) = forEach { validator ->
            if (!validator.validate(ast)) {
                // TODO: improve exception
                throw IllegalStateException("Validator `${validator::class.simpleName}` failed!")
            }
        }

    }

}
