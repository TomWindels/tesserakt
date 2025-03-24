package dev.tesserakt.sparql.runtime.evaluation

import dev.tesserakt.rdf.types.Quad

object GlobalQueryContext: QueryContext {

    private val bindings = mutableListOf<String>()
    private val bindingsLut = mutableMapOf<String, Int>()

    private val terms = mutableMapOf<Quad.Term, Int>()
    private val termsLut = mutableMapOf<Int, Quad.Term>()

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
            termsLut[i] = value
            i
        }
    }

    override fun resolveBinding(id: Int): String {
        return bindings[id]
    }

    override fun resolveTerm(id: Int): Quad.Term {
        return termsLut[id]!!
    }

}
