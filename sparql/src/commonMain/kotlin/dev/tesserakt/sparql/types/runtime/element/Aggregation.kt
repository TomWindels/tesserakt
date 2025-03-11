package dev.tesserakt.sparql.types.runtime.element

import dev.tesserakt.sparql.compiler.lexer.Token

data class Aggregation(
    val expression: Expression,
    val target: Token.Binding
): RuntimeElement
