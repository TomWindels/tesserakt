package dev.tesserakt.sparql.ast

import dev.tesserakt.sparql.compiler.lexer.Token
import kotlin.jvm.JvmInline

sealed interface Filter: QueryAtom {

    @JvmInline
    value class Predicate(val expression: Expression.Conditional): Filter

    data class Regex(
        val input: Token.Binding,
        val regex: String,
        val mode: String
    ): Filter

}
