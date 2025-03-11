package dev.tesserakt.sparql.ast

import dev.tesserakt.sparql.compiler.lexer.Token

data class Aggregation(
    val expression: Expression,
    val target: Token.Binding
): QueryAtom
