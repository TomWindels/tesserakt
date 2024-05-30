package dev.tesserakt.sparql.runtime.common.compat

import dev.tesserakt.sparql.compiler.ast.ASTNode
import dev.tesserakt.sparql.runtime.node.CommonNode

abstract class CommonCompatLayer<I: ASTNode, O: CommonNode> {

    abstract fun convert(source: I): O

    protected fun bail(message: String = "Conversion error in ${this::class.simpleName}"): Nothing {
        throw CommonCompatException(message = message)
    }

}
