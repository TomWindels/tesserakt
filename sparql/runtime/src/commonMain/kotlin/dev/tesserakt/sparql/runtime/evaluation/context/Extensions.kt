package dev.tesserakt.sparql.runtime.evaluation.context

import dev.tesserakt.rdf.types.EncodedQuad
import dev.tesserakt.rdf.types.Quad

fun QueryContext.encode(quad: Quad): EncodedQuad {
    val s = resolveTerm(quad.s)
    val p = resolveTerm(quad.p)
    val o = resolveTerm(quad.o)
    val g = resolveTerm(quad.g)
    return EncodedQuad(s, p, o, g)
}

fun QueryContext.decode(quad: EncodedQuad): Quad {
    val s = resolveTerm(quad.s) as Quad.Subject
    val p = resolveTerm(quad.p) as Quad.Predicate
    val o = resolveTerm(quad.o) as Quad.Object
    val g = resolveTerm(quad.g) as Quad.Graph
    return Quad(s, p, o, g)
}
