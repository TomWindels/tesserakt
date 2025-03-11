package dev.tesserakt.sparql.ast

data class Aggregation(
    val expression: Expression,
    val target: Binding
): QueryAtom
