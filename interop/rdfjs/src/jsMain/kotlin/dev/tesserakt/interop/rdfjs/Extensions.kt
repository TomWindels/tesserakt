package dev.tesserakt.interop.rdfjs

import dev.tesserakt.interop.rdfjs.n3.*
import dev.tesserakt.rdf.ontology.XSD
import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.rdf.types.Store

fun Store.toN3Store(): N3Store {
    val result = N3Store()
    result.addAll(map { it.toN3Triple() }.toTypedArray())
    return result
}

fun Quad.toN3Triple() = N3Quad(
    subject = s.toN3Term(),
    predicate = p.toN3Term(),
    `object` = o.toN3Term()
)

fun Quad.Term.toN3Term() = when (this) {
    is Quad.NamedTerm -> createN3NamedNode(value)
    is Quad.Literal<*> -> createN3Literal(value, createN3NamedNode(type.value))
    is Quad.BlankTerm -> createN3NamedNode("_:b_$id")
}

fun N3Quad.toQuad() = Quad(
    s = subject.toTerm(),
    p = predicate.toTerm() as Quad.NamedTerm,
    o = `object`.toTerm()
)

fun N3Term.toTerm(): Quad.Term = when (termType) {
    "NamedNode" -> unsafeCast<N3NamedNode>().toTerm()

    "Literal" -> unsafeCast<N3Literal>().toTerm()

    "BlankNode" -> unsafeCast<N3BlankNode>().toTerm()

    "Variable" -> throw IllegalArgumentException("Term `$this` is not supported as a quad term!")

    else -> throw IllegalArgumentException("Unknown term type `$termType`!")
}

fun N3NamedNode.toTerm() = Quad.NamedTerm(value = value)

private object N3XSD {
    val string = XSD.string.toN3Term()
    val int = XSD.int.toN3Term()
    val integer = XSD.integer.toN3Term()
    val long = XSD.long.toN3Term()
    val float = XSD.float.toN3Term()
    val double = XSD.double.toN3Term()
    val boolean = XSD.boolean.toN3Term()
}

fun N3Literal.toTerm() = when (datatype) {
    N3XSD.string -> Quad.Literal(value, type = XSD.string)
    N3XSD.int, N3XSD.integer -> Quad.Literal(value.toInt(), type = XSD.int)
    N3XSD.long -> Quad.Literal(value.toLong(), type = XSD.long)
    N3XSD.float -> Quad.Literal(value.toFloat(), type = XSD.float)
    N3XSD.double -> Quad.Literal(value.toDouble(), type = XSD.double)
    N3XSD.boolean -> Quad.Literal(value.toBoolean(), type = XSD.boolean)
    else -> throw IllegalArgumentException("Unknown datatype `${datatype.value}`")
}

fun N3BlankNode.toTerm() = Quad.BlankTerm(id = value.takeLastWhile { it.isDigit() }.toInt())
