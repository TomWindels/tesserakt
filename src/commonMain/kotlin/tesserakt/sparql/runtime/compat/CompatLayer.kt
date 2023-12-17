package tesserakt.sparql.runtime.compat

import tesserakt.sparql.compiler.types.AST
import tesserakt.sparql.runtime.types.ASTr

abstract class CompatLayer<I: AST, O: ASTr> {

    abstract fun convert(source: I): O

    protected fun bail(message: String = "Conversion error in ${this::class.simpleName}"): Nothing {
        throw CompatException(message = message)
    }

}
