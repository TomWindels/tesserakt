package dev.tesserakt.sparql.runtime.query

import dev.tesserakt.sparql.runtime.evaluation.context.QueryContext
import dev.tesserakt.sparql.types.BindingStatement

class BindingExpression(
    context: QueryContext,
    statement: BindingStatement,
) {

    val target = statement.target
    val operation = Expression.Operation.from(context, statement.expression)

    override fun toString(): String {
        return "BIND ($operation) AS $target"
    }

}
