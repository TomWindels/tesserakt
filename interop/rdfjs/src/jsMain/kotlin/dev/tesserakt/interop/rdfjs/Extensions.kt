package dev.tesserakt.interop.rdfjs

import dev.tesserakt.interop.rdfjs.n3.*
import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.rdf.types.Store

fun Collection<Quad>.toN3Store(): N3Store {
    val result = N3Store()
    result.addAll(map { it.toN3Triple() }.toTypedArray())
    return result
}

fun Quad.toN3Triple() = N3Quad(
    subject = s.toN3Term(),
    predicate = p.toN3Term(),
    `object` = o.toN3Term(),
    graph = g.toN3GraphTerm(),
)

fun Quad.Term.toN3Term() = when (this) {
    is Quad.NamedTerm -> createN3NamedNode(value)
    is Quad.Literal -> createN3Literal(value, createN3NamedNode(type.value))
    is Quad.BlankTerm -> createN3NamedNode("_:b_$id")
}

private val DefaultN3Graph = object: N3Term {
    override val termType: String = "DefaultGraph"
    override val value: String = "DefaultGraph"
}

fun Quad.Graph.toN3GraphTerm() = when (this) {
    is Quad.NamedTerm -> createN3NamedNode(value)
    is Quad.BlankTerm -> createN3NamedNode("_:b_$id")
    Quad.DefaultGraph -> DefaultN3Graph
}

fun N3Store.toStore(): Store {
    val result = Store()
    forEach(callback = { quad ->
        result.add(quad.toQuad())
    })
    return result
}

fun N3Quad.toQuad() = Quad(
    s = subject.toTerm(),
    p = predicate.toTerm() as Quad.NamedTerm,
    o = `object`.toTerm(),
    g = graph.toGraphTerm()
)

fun N3Term.toTerm(): Quad.Term = when (termType) {
    "NamedNode" -> unsafeCast<N3NamedNode>().toTerm()

    "Literal" -> unsafeCast<N3Literal>().toTerm()

    "BlankNode" -> unsafeCast<N3BlankNode>().toTerm()

    "Variable" -> throw IllegalArgumentException("Term `$this` is not supported as a quad term!")

    else -> throw IllegalArgumentException("Unknown term type `$termType`!")
}

fun N3NamedNode.toTerm() = Quad.NamedTerm(value = value)

fun N3Literal.toTerm() = Quad.Literal(value = value, type = datatype.toTerm())

fun N3BlankNode.toTerm() = Quad.BlankTerm(id = value.takeLastWhile { it.isDigit() }.toInt())

fun N3Term.toGraphTerm(): Quad.Graph = when {
    termType == "DefaultGraph" -> Quad.DefaultGraph
    else -> toTerm() as Quad.Graph
}
