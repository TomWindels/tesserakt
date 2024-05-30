package dev.tesserakt.sparql.compiler.ast

import dev.tesserakt.sparql.compiler.lexer.Token

data class AggregationAST(
    val expression: ExpressionAST,
    val target: Token.Binding
): ASTNode
