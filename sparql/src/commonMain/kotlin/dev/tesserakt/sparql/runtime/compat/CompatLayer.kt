package dev.tesserakt.sparql.runtime.compat

import dev.tesserakt.sparql.compiler.ast.ASTNode
import dev.tesserakt.sparql.runtime.types.ASTr

abstract class CompatLayer<I: ASTNode, O: ASTr> {

    abstract fun convert(source: I): O

    protected fun bail(message: String = "Conversion error in ${this::class.simpleName}"): Nothing {
        throw CompatException(message = message)
    }

}
