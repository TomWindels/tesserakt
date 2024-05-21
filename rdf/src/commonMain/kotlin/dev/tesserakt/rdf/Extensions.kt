package dev.tesserakt.rdf

import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.rdf.types.Quad.Companion.asLiteral
import dev.tesserakt.rdf.types.Quad.Companion.asNamedTerm

val String.nt: Quad.NamedTerm
    get() = asNamedTerm()

val String.lt: Quad.Literal<String>
    get() = asLiteral()

val Int.lt: Quad.Literal<Int>
    get() = asLiteral()

val Long.lt: Quad.Literal<Long>
    get() = asLiteral()

val Float.lt: Quad.Literal<Float>
    get() = asLiteral()

val Double.lt: Quad.Literal<Double>
    get() = asLiteral()

val Boolean.lt: Quad.Literal<Boolean>
    get() = asLiteral()
