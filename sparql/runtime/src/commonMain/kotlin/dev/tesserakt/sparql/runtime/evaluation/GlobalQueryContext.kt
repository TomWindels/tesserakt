package dev.tesserakt.sparql.runtime.evaluation

import dev.tesserakt.rdf.types.Quad

object GlobalQueryContext: QueryContext {

    private val bindings = mutableListOf<String>()
    private val bindingsLut = mutableMapOf<String, Int>()

    private val terms = mutableMapOf<Quad.Term, Int>()
    // as terms are never removed from an active context, we can keep it as a regular list without risking IDs
    // shifting over
    private val termsLut = mutableListOf<Quad.Term>()

    override fun resolveBinding(value: String): Int {
        return bindingsLut.getOrPut(value) {
            val i = bindings.size
            require(bindingsLut.size == i)
            bindings.add(value)
            i
        }
    }

    override fun resolveTerm(value: Quad.Term): Int {
        return terms.getOrPut(value) {
            val i = terms.size
            termsLut.add(value)
            i
        }
    }

    override fun resolveBinding(id: Int): String {
        return bindings[id]
    }

    override fun resolveTerm(id: Int): Quad.Term {
        return termsLut[id]
    }

}
