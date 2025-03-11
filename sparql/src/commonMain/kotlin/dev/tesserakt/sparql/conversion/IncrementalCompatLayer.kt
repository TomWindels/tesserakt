package dev.tesserakt.sparql.conversion

import dev.tesserakt.sparql.types.ast.ASTElement
import dev.tesserakt.sparql.types.runtime.element.RuntimeElement

abstract class IncrementalCompatLayer<I: ASTElement, O: RuntimeElement> {

    abstract fun convert(source: I): O

    protected fun bail(message: String = "Conversion error in ${this::class.simpleName}"): Nothing {
        throw IncrementalCompatException(message = message)
    }

}