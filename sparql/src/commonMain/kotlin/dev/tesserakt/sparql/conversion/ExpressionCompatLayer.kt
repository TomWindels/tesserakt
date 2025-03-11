package dev.tesserakt.sparql.conversion

import dev.tesserakt.sparql.types.ast.ExpressionAST
import dev.tesserakt.sparql.types.runtime.element.Expression

object ExpressionCompatLayer: CommonCompatLayer<ExpressionAST, Expression>() {

    override fun convert(source: ExpressionAST): Expression {
        return Expression.convert(source)
    }

}
