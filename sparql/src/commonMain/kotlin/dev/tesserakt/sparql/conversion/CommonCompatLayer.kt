package dev.tesserakt.sparql.conversion

import dev.tesserakt.sparql.types.ast.ASTElement

abstract class CommonCompatLayer<I: ASTElement, O> {

    abstract fun convert(source: I): O

    protected fun bail(message: String = "Conversion error in ${this::class.simpleName}"): Nothing {
        throw CommonCompatException(message = message)
    }

}
