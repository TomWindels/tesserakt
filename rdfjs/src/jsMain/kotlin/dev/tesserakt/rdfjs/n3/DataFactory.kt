@file:JsModule("n3")
@file:JsNonModule
@file:JsQualifier("DataFactory")
package dev.tesserakt.rdfjs.n3

@JsName("namedNode")
internal external fun createN3NamedNode(value: String): N3NamedNode

@JsName("literal")
internal external fun createN3Literal(value: Any, type: N3NamedNode = definedExternally): N3Literal
