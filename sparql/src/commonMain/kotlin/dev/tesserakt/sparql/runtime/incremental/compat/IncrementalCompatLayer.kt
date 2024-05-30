package dev.tesserakt.sparql.runtime.incremental.compat

import dev.tesserakt.sparql.compiler.ast.ASTNode
import dev.tesserakt.sparql.runtime.node.IncrementalNode

abstract class IncrementalCompatLayer<I: ASTNode, O: IncrementalNode> {

    abstract fun convert(source: I): O

    protected fun bail(message: String = "Conversion error in ${this::class.simpleName}"): Nothing {
        throw IncrementalCompatException(message = message)
    }

}
