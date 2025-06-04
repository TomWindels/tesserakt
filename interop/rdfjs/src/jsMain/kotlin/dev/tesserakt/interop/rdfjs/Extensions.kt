package dev.tesserakt.interop.rdfjs

import dev.tesserakt.interop.rdfjs.n3.*
import dev.tesserakt.rdf.ontology.XSD
import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.rdf.types.Store
import dev.tesserakt.rdf.types.factory.MutableStore

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

fun Quad.Element.toN3Term() = when (this) {
    is Quad.NamedTerm -> createN3NamedNode(value)
    is Quad.Literal -> createN3Literal(value, createN3MappedLiteralDType(type))
    is Quad.LangString -> createN3Literal(value, language)
    is Quad.BlankTerm -> createN3BlankNode("_:b_$id")
    Quad.DefaultGraph -> DefaultN3Graph
}

fun createN3MappedLiteralDType(term: Quad.NamedTerm): N3NamedNode {
    return when (term) {
        XSD.int, XSD.integer -> createN3NamedNode(XSD.integer.value)
        else -> createN3NamedNode(term.value)
    }
}

private val DefaultN3Graph = object: N3Term {
    override val termType: String = "DefaultGraph"
    override val value: String = ""
}

fun Quad.Graph.toN3GraphTerm() = when (this) {
    is Quad.NamedTerm -> createN3NamedNode(value)
    is Quad.BlankTerm -> createN3BlankNode("_:b_$id")
    Quad.DefaultGraph -> DefaultN3Graph
}

fun N3Store.toStore(): Store {
    val result = MutableStore()
    forEach(callback = { quad ->
        result.add(quad.toQuad())
    })
    return result
}

fun N3Quad.toQuad() = Quad(
    s = subject.toTerm().jsCastOrBail(),
    p = predicate.toTerm().jsCastOrBail(),
    o = `object`.toTerm().jsCastOrBail(),
    g = graph.toGraphTerm()
)

private inline fun <reified T> Any.jsCastOrBail(): T {
    return this as? T ?: throw Error("Invalid type: ${this::class.simpleName}\nExpected ${T::class.simpleName}")
}

fun N3Term.toTerm(): Quad.Element = when (termType) {
    "NamedNode" -> unsafeCast<N3NamedNode>().toTerm()

    "Literal" -> unsafeCast<N3Literal>().toTerm()

    "BlankNode" -> unsafeCast<N3BlankNode>().toTerm()

    "Variable" -> throw IllegalArgumentException("Term `$this` is not supported as a quad term!")

    else -> throw IllegalArgumentException("Unknown term type `$termType`!")
}

fun N3NamedNode.toTerm() = Quad.NamedTerm(value = value)

fun N3Literal.toTerm() = when {
    language.isNotBlank() -> Quad.LangString(value = value, language = language)
    else -> Quad.Literal(value = value, type = datatype.toTerm())
}

fun N3BlankNode.toTerm() = Quad.BlankTerm(id = value.takeLastWhile { it.isDigit() }.toInt())

fun N3Term.toGraphTerm(): Quad.Graph = when {
    termType == "DefaultGraph" -> Quad.DefaultGraph
    else -> toTerm() as Quad.Graph
}
