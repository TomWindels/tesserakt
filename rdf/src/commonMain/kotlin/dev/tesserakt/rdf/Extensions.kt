package dev.tesserakt.rdf

import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.rdf.types.Quad.Companion.asLiteral
import dev.tesserakt.rdf.types.Quad.Companion.asNamedTerm

val String.namedTerm: Quad.NamedTerm
    get() = asNamedTerm()

val String.literalTerm: Quad.Literal<String>
    get() = asLiteral()

val Int.literalTerm: Quad.Literal<Int>
    get() = asLiteral()

val Long.literalTerm: Quad.Literal<Long>
    get() = asLiteral()

val Float.literalTerm: Quad.Literal<Float>
    get() = asLiteral()

val Double.literalTerm: Quad.Literal<Double>
    get() = asLiteral()

val Boolean.literalTerm: Quad.Literal<Boolean>
    get() = asLiteral()
