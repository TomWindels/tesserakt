package dev.tesserakt.sparql.runtime.evaluation

import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.sparql.Bindings
import dev.tesserakt.sparql.runtime.evaluation.context.QueryContext
import dev.tesserakt.sparql.runtime.evaluation.mapping.Mapping
import dev.tesserakt.sparql.runtime.query.BindingExpression
import dev.tesserakt.sparql.runtime.query.Expression.getTerm

class BindingsImpl(
    context: QueryContext,
    private val mapping: Mapping,
    expressions: Collection<BindingExpression>
): Bindings {

    // we don't care about the result of the expressions as these are considered to be consistent for the same
    //  exact term values
    private val hashCode = mapping.data.contentHashCode()

    private val terms = buildList(mapping.count + expressions.size) {
        mapping.asIterable().forEach { (bId, tId) ->
            val binding = bId.name ?: context.resolveBinding(bId.id)
            add(binding to context.resolveTerm(tId.id))
        }
        expressions.forEach { expression ->
            val binding = expression.target.name
            val term = expression.operation.eval(mapping).getTerm(context)
                // unbound terms are not added to our list
                ?: return@forEach
            add(binding to term)
        }
    }

    override fun iterator(): Iterator<Pair<String, Quad.Element>> = terms.iterator()

    override fun hashCode(): Int {
        return hashCode
    }

    override fun equals(other: Any?): Boolean {
        if (other !is BindingsImpl) {
            return false
        }
        return this.mapping.matches(other.mapping)
    }

    override fun toString() = terms.joinToString(prefix = "{", postfix = "}") { "${it.first} = ${it.second}" }

}
