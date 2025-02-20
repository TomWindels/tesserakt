package dev.tesserakt.interop.jena

import dev.tesserakt.rdf.ontology.XSD
import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.rdf.types.Quad.Companion.asNamedTerm
import org.apache.jena.datatypes.RDFDatatype
import org.apache.jena.datatypes.xsd.XSDDatatype
import org.apache.jena.graph.*
import org.apache.jena.query.Dataset
import org.apache.jena.query.DatasetFactory
import org.apache.jena.query.ReadWrite

fun Collection<Quad>.toJenaDataset(): Dataset {
    val store = DatasetFactory.createTxnMem()
    store.begin(ReadWrite.WRITE)
    try {
        val graph = store.asDatasetGraph()
        forEach { graph.add(it.toJenaQuad()) }
        store.commit()
    } finally {
        store.end()
    }
    return store
}

fun Quad.toJenaQuad(): org.apache.jena.sparql.core.Quad {
    return org.apache.jena.sparql.core.Quad.create(
        /* g = */ org.apache.jena.sparql.core.Quad.defaultGraphIRI,
        /* s = */ s.toJenaTerm(),
        /* p = */ p.toJenaTerm(),
        /* o = */ o.toJenaTerm()
    )
}

fun Quad.Term.toJenaTerm() = when (this) {
    is Quad.NamedTerm -> NodeFactory.createURI(value)
    is Quad.Literal -> NodeFactory.createLiteral(value, type.asRDFDataType())
    is Quad.BlankTerm -> NodeFactory.createBlankNode(value)
}

private fun Quad.NamedTerm.asRDFDataType(): RDFDatatype = when (this) {
    XSD.string -> XSDDatatype.XSDstring
    XSD.boolean -> XSDDatatype.XSDboolean
    XSD.int -> XSDDatatype.XSDint
    XSD.long -> XSDDatatype.XSDlong
    XSD.float -> XSDDatatype.XSDfloat
    XSD.double -> XSDDatatype.XSDdouble
    XSD.duration -> XSDDatatype.XSDduration
    XSD.dateTime -> XSDDatatype.XSDdateTime
    XSD.time -> XSDDatatype.XSDtime
    XSD.date -> XSDDatatype.XSDdate
    else -> throw IllegalArgumentException("Unknown type: `$value`")
}

fun Node.toTerm() = when (this) {
    is Node_URI -> Quad.NamedTerm(value = uri)
    is Node_Literal -> Quad.Literal(
        value = literalValue.toString(),
        type = literalDatatype.uri.asNamedTerm()
    )
    is Node_Blank -> Quad.BlankTerm(id = blankNodeLabel.takeLastWhile { it.isDigit() }.toInt())
    else -> throw IllegalArgumentException("Unknown node type `${this::class.simpleName}`")
}

fun Triple.toQuad() = Quad(
    s = subject.toTerm(),
    p = predicate.toTerm() as Quad.NamedTerm,
    o = `object`.toTerm()
)
