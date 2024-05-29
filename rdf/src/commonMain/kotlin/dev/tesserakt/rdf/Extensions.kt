package dev.tesserakt.rdf

import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.rdf.types.Quad.Companion.asLiteralTerm
import dev.tesserakt.rdf.types.Quad.Companion.asNamedTerm

val String.namedTerm: Quad.NamedTerm
    get() = asNamedTerm()

val String.literalTerm: Quad.Literal<String>
    get() = asLiteralTerm()

val Int.literalTerm: Quad.Literal<Int>
    get() = asLiteralTerm()

val Long.literalTerm: Quad.Literal<Long>
    get() = asLiteralTerm()

val Float.literalTerm: Quad.Literal<Float>
    get() = asLiteralTerm()

val Double.literalTerm: Quad.Literal<Double>
    get() = asLiteralTerm()

val Boolean.literalTerm: Quad.Literal<Boolean>
    get() = asLiteralTerm()
