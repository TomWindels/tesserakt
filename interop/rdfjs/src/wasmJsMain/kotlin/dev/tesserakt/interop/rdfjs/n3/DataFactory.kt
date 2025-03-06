@file:JsModule("n3")
@file:JsQualifier("DataFactory")
package dev.tesserakt.interop.rdfjs.n3

@JsName("namedNode")
internal external fun createN3NamedNode(value: JsString): N3NamedNode

@JsName("literal")
internal external fun createN3Literal(value: JsAny, type: N3NamedNode = definedExternally): N3Literal
