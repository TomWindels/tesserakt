@file:JsModule("n3")
@file:JsNonModule
package dev.tesserakt.interop.rdfjs.n3

@JsName("Triple")
external class N3Quad(
    subject: N3Term,
    predicate: N3Term,
    `object`: N3Term,
    graph: N3Term? = definedExternally
) {

    constructor(
        subject: N3Term,
        predicate: N3Term,
        `object`: N3Term,
    )

    val subject: N3Term
    val predicate: N3Term
    val `object`: N3Term
    val graph: N3Term

}

@JsName("Term")
external interface N3Term {
    val termType: String
    val value: String
}

@JsName("NamedNode")
external interface N3NamedNode: N3Term

@JsName("BlankNode")
external interface N3BlankNode: N3Term

@JsName("Literal")
external interface N3Literal: N3Term {
    /**
     * The language as lowercase BCP47 string (examples: en, en-gb)
     * or an empty string if the literal has no language.
     * @link http://tools.ietf.org/html/bcp47
     */
    val language: String
    /**
     * A NamedNode whose IRI represents the datatype of the literal.
     */
    val datatype: N3NamedNode
}

@JsName("Store")
external class N3Store {

    val size: Int

    fun add(triple: N3Quad)
    @JsName("addQuad")
    fun add(subject: N3Term, predicate: N3Term, `object`: N3Term, graph: N3Term? = definedExternally)
    @JsName("addQuads")
    fun addAll(triples: Array<N3Quad>)
    fun has(triple: N3Quad)
    fun delete(triple: N3Quad)
    fun forEach(
        callback: (N3Quad) -> Unit,
        subject: N3Term? = definedExternally,
        predicate: N3Term? = definedExternally,
        `object`: N3Term? = definedExternally
    )
    fun getSubjects(): Array<N3Term>
    fun getPredicates(): Array<N3Term>
    fun getObjects(): Array<N3Term>
    fun getQuads(
        subject: N3Term = definedExternally,
        predicate: N3Term = definedExternally,
        `object`: N3Term = definedExternally,
        graph: N3Term = definedExternally
    ): Array<N3Quad>
    fun createBlankNode(suggestedName: String = definedExternally): N3BlankNode
}
