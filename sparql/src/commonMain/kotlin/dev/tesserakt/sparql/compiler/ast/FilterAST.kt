package dev.tesserakt.sparql.compiler.ast

import dev.tesserakt.sparql.compiler.lexer.Token
import kotlin.jvm.JvmInline

sealed interface FilterAST: ASTNode {

    @JvmInline
    value class Predicate(val expression: ExpressionAST.Conditional): FilterAST

    data class Regex(
        val input: Token.Binding,
        val regex: String,
        val mode: String
    ): FilterAST

}
