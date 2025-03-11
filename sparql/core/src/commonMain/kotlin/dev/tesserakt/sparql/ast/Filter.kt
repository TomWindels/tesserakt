package dev.tesserakt.sparql.ast

import kotlin.jvm.JvmInline

sealed interface Filter: QueryAtom {

    @JvmInline
    value class Predicate(val expression: Expression.Conditional): Filter

    data class Regex(
        val input: Binding,
        val regex: String,
        val mode: String
    ): Filter

}
