package tesserakt.sparql.compiler.validator

import tesserakt.sparql.compiler.types.QueryAST
import kotlin.reflect.KClass

abstract class Validator<AST: QueryAST>(private val clazz: KClass<AST>) {

    @Suppress("UNCHECKED_CAST")
    fun validate(ast: QueryAST): Boolean = !clazz.isInstance(ast) || _validate(ast as AST)

    protected abstract fun _validate(ast: AST): Boolean

    companion object {

        fun Iterable<Validator<*>>.validate(ast: QueryAST) = forEach { validator ->
            if (!validator.validate(ast)) {
                // TODO: improve exception
                throw IllegalStateException("Validator `${validator::class.simpleName}` failed!")
            }
        }

    }

}
