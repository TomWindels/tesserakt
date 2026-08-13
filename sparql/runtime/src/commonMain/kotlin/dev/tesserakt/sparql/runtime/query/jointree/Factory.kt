package dev.tesserakt.sparql.runtime.query.jointree

import dev.tesserakt.sparql.runtime.evaluation.BindingIdentifierSet
import dev.tesserakt.sparql.runtime.evaluation.context.QueryContext
import dev.tesserakt.sparql.runtime.query.FilterExpression
import dev.tesserakt.sparql.runtime.query.MutableJoinState
import kotlin.jvm.JvmName


@JvmName("fromStates")
fun JoinTree.Companion.from(
    context: QueryContext,
    states: List<MutableJoinState>,
    filters: List<FilterExpression>,
    externalBindings: BindingIdentifierSet,
) = when {
    states.isEmpty() -> EmptyJoinTree
    else -> DynamicJoinTree(context, states, filters, externalBindings)
}
