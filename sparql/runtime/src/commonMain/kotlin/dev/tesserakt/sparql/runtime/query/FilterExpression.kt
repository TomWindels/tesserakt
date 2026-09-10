package dev.tesserakt.sparql.runtime.query

import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.sparql.runtime.evaluation.context.QueryContext
import dev.tesserakt.sparql.runtime.evaluation.mapping.Mapping
import dev.tesserakt.sparql.runtime.query.Expression.getTerm
import dev.tesserakt.sparql.types.Expression as CompiledExpression

class FilterExpression(val context: QueryContext, expr: CompiledExpression) {

    private val root = Expression.BooleanCoercionOperation(context, parent = Expression.Operation.from(context, expr))
    val bindings = root.parent.bindings()

    fun test(mapping: Mapping): Boolean {
        return root.eval(mapping).getTerm(context) == Quad.Literal(true)
    }

    override fun toString(): String {
        // we go one operation node deeper as a filter always coerces into a boolean
        return root.parent.toString()
    }

}
