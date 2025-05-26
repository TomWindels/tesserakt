@file:JsModule("n3")
@file:JsNonModule
@file:JsQualifier("DataFactory")
package dev.tesserakt.interop.rdfjs.n3

@JsName("blankNode")
internal external fun createN3BlankNode(value: String): N3BlankNode

@JsName("namedNode")
internal external fun createN3NamedNode(value: String): N3NamedNode

@JsName("literal")
internal external fun createN3Literal(value: Any, type: N3NamedNode = definedExternally): N3Literal
