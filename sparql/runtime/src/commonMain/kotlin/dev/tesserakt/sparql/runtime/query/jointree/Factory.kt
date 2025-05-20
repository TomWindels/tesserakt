package dev.tesserakt.sparql.runtime.query.jointree

import dev.tesserakt.sparql.runtime.evaluation.QueryContext
import dev.tesserakt.sparql.types.TriplePattern
import dev.tesserakt.sparql.types.Union
import kotlin.jvm.JvmName


@JvmName("fromPatterns")
fun JoinTree.Companion.from(context: QueryContext, patterns: List<TriplePattern>) = when {
    // TODO(perf) specialised empty case
    // TODO(perf) also based on binding overlap
    patterns.size >= 2 -> DynamicJoinTree(context, patterns)
    patterns.isEmpty() -> EmptyJoinTree
    else -> StatelessJoinTree(context, patterns)
}

@JvmName("fromUnions")
fun JoinTree.Companion.from(context: QueryContext, unions: List<Union>) = when {
    // TODO(perf) specialised empty case
    // TODO(perf) also based on binding overlap
    unions.size >= 2 -> DynamicJoinTree(context, unions)
    unions.isEmpty() -> EmptyJoinTree
    else -> StatelessJoinTree(context, unions)
}
