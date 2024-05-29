package dev.tesserakt.sparql.compiler.ast

import dev.tesserakt.sparql.compiler.lexer.Token

data class Aggregation(
    val expression: Expression,
    val target: Token.Binding
): ASTNode
