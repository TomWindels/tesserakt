package dev.tesserakt.sparql.runtime.compat

import dev.tesserakt.sparql.types.*
import dev.tesserakt.sparql.util.getAllNamedBindings

/*
 * As inner filters have access to variable values from outer scopes, these values have to be available during
 *  evaluation. Considering how the runtime internally works, these variable values are not available at all stages of
 *  the evaluation. To combat this problem, these filter group patterns are expanded, pushing these variables into
 *  scope.
 */

object InnerFilterScopeCompat {

    fun apply(body: GraphPattern): GraphPattern {
        val fixed = fixProblematicExpressionReferences(body)
        return fixed
    }

    private fun fixProblematicExpressionReferences(body: GraphPattern): GraphPattern {
        // getting all problematic filter expressions in this body:
        // `FILTER(expr)` statements referencing external variables found inside `FILTER ... {}` blocks
        val filters = body.filters.toMutableList()
        filters.mapNotNullInPlace { filter ->
            when (filter) {
                // all "one level deep" expressions have to be checked: if they reference bindings not in the
                //  local scope, they are problematic
                is Filter.Exists -> {
                    val missing = extractAllExpressions(filter.pattern)
                        .flatMapTo(mutableSetOf()) { expression -> extractExpressionVariables(expression) }.minus(filter.pattern.getAllNamedBindings().mapTo(mutableSetOf()) { it.name })
                    if (missing.isEmpty()) {
                        return@mapNotNullInPlace null
                    }
                    val extra = missing.flatMap { getPatterns(it, body) }.distinct()
                    Filter.Exists(
                        pattern = filter.pattern.copy(patterns = TriplePatternSet(filter.pattern.patterns + extra))
                    )
                }
                is Filter.NotExists -> {
                    val missing = extractAllExpressions(filter.pattern)
                        .flatMapTo(mutableSetOf()) { expression -> extractExpressionVariables(expression) }.minus(filter.pattern.getAllNamedBindings().mapTo(mutableSetOf()) { it.name })
                    if (missing.isEmpty()) {
                        return@mapNotNullInPlace null
                    }
                    val extra = missing.flatMap { getPatterns(it, body) }.distinct()
                    Filter.NotExists(
                        pattern = filter.pattern.copy(patterns = TriplePatternSet(filter.pattern.patterns + extra))
                    )
                }
                // these two aren't problematic on their own
                is Filter.Predicate -> null
                is Filter.Regex -> null
            }
        }
        return body.copy(filters = filters)
    }

    private fun extractAllExpressions(body: GraphPattern): List<Expression> {
        val one = body.filters.mapNotNull { (it as? Filter.Predicate)?.expression }
        val two = body.unions.flatMap { union ->
            union.segments.flatMap { segment ->
                (segment as? GraphPatternSegment)
                    ?.pattern
                    ?.let { unionPatternSegment -> extractAllExpressions(unionPatternSegment) }
                    ?: emptyList()
            }
        }
        return one + two
    }

    private fun getPatterns(name: String, body: GraphPattern): List<TriplePattern> {
        return body.patterns.filter { it.s.isBinding(name) || it.p.isBinding(name) || it.o.isBinding(name) }
    }

    private fun TriplePattern.Element.isBinding(name: String): Boolean {
        return this is TriplePattern.Binding && this.name == name
    }

    private fun extractExpressionVariables(expression: Expression): Set<String> {
        return when (expression) {
            is Expression.BindingAggregate -> setOf(expression.input.name)
            is Expression.BindingValues -> setOf(expression.name)
            is Expression.FuncCall -> expression.args.flatMapTo(mutableSetOf()) { extractExpressionVariables(it) }
            is Expression.Comparison -> extractExpressionVariables(expression.lhs) + extractExpressionVariables(expression.rhs)
            is Expression.MathOp -> extractExpressionVariables(expression.lhs) + extractExpressionVariables(expression.rhs)
            is Expression.Negative -> extractExpressionVariables(expression.value)
            is Expression.NumericLiteralValue -> emptySet()
            is Expression.StringLiteralValue -> emptySet()
        }
    }

}

private inline fun <E: Any> MutableList<E>.mapNotNullInPlace(mapper: (E) -> E?) {
    var i = -1
    while (++i < size) {
        val mapped = mapper(this[i]) ?: continue
        this[i] = mapped
    }
}
