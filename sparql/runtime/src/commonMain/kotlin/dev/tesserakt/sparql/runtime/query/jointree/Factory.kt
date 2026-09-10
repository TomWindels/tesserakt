package dev.tesserakt.sparql.runtime.query.jointree

import dev.tesserakt.sparql.runtime.evaluation.BindingIdentifierSet
import dev.tesserakt.sparql.runtime.query.FilterExpression
import dev.tesserakt.sparql.runtime.query.MutableJoinState
import kotlin.jvm.JvmName


@JvmName("fromStates")
fun JoinTree.Companion.from(
    states: List<MutableJoinState>,
    filters: List<FilterExpression>,
    externalBindings: BindingIdentifierSet,
) = when {
    states.isEmpty() -> EmptyJoinTree
    else -> DynamicJoinTree(states, filters, externalBindings)
}
