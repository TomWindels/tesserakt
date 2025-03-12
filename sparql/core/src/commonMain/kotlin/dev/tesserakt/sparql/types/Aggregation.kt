package dev.tesserakt.sparql.types

data class Aggregation(
    val expression: Expression,
    val target: Binding
): QueryAtom
