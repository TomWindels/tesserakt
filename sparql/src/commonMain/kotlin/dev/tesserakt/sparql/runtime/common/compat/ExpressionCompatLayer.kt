package dev.tesserakt.sparql.runtime.common.compat

import dev.tesserakt.sparql.compiler.ast.ExpressionAST
import dev.tesserakt.sparql.runtime.common.types.Expression

object ExpressionCompatLayer: CommonCompatLayer<ExpressionAST, Expression>() {

    override fun convert(source: ExpressionAST): Expression {
        return Expression.convert(source)
    }

}
