package tesserakt.rdf

import tesserakt.rdf.types.Triple
import tesserakt.rdf.types.Triple.Companion.asLiteral
import tesserakt.rdf.types.Triple.Companion.asNamedTerm

val String.nt: Triple.NamedTerm
    get() = asNamedTerm()

val String.lt: Triple.Literal<String>
    get() = asLiteral()

val Int.lt: Triple.Literal<Int>
    get() = asLiteral()

val Long.lt: Triple.Literal<Long>
    get() = asLiteral()

val Float.lt: Triple.Literal<Float>
    get() = asLiteral()

val Double.lt: Triple.Literal<Double>
    get() = asLiteral()

val Boolean.lt: Triple.Literal<Boolean>
    get() = asLiteral()
