package dev.tesserakt.sparql.types

import kotlin.jvm.JvmInline

sealed interface Filter: QueryAtom {

    @JvmInline
    value class Predicate(val expression: Expression.Comparison): Filter

    data class Regex(
        val input: Binding,
        val regex: String,
        val mode: String
    ): Filter

    @JvmInline
    value class Exists(val pattern: GraphPattern): Filter

    @JvmInline
    value class NotExists(val pattern: GraphPattern): Filter

}
