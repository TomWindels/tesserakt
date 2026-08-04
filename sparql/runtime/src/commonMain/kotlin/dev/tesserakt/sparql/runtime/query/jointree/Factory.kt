package dev.tesserakt.sparql.runtime.query.jointree

import dev.tesserakt.sparql.runtime.evaluation.BindingIdentifierSet
import dev.tesserakt.sparql.runtime.evaluation.context.QueryContext
import dev.tesserakt.sparql.runtime.query.FilterExpression
import dev.tesserakt.sparql.runtime.query.TriplePatternState
import dev.tesserakt.sparql.types.TriplePattern
import dev.tesserakt.sparql.types.Union
import kotlin.jvm.JvmName


@JvmName("fromPatterns")
fun JoinTree.Companion.from(
    context: QueryContext,
    patterns: List<TriplePattern>,
    filters: List<FilterExpression>,
    externalBindings: BindingIdentifierSet,
) = when {
    patterns.size >= 2 -> DynamicJoinTree(context, patterns, filters, externalBindings)
    patterns.isEmpty() -> EmptyJoinTree
    else -> SingleItemJoinTree(context, patterns, filters)
}

@JvmName("fromPatterns")
fun JoinTree.Companion.from(
    patterns: List<TriplePatternState<*>>,
    filters: List<FilterExpression>,
    externalBindings: BindingIdentifierSet,
) = when {
    patterns.size >= 2 -> DynamicJoinTree(patterns, filters, externalBindings)
    patterns.isEmpty() -> EmptyJoinTree
    else -> SingleItemJoinTree(patterns, filters)
}

@JvmName("fromUnions")
fun JoinTree.Companion.from(
    context: QueryContext,
    unions: List<Union>,
    filters: List<FilterExpression>,
    externalBindings: BindingIdentifierSet,
) = when {
    unions.size >= 2 -> DynamicJoinTree(context, unions, filters, externalBindings)
    unions.isEmpty() -> EmptyJoinTree
    else -> SingleItemJoinTree(context, unions, filters)
}
