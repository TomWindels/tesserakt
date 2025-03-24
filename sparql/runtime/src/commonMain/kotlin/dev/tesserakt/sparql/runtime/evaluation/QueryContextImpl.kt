package dev.tesserakt.sparql.runtime.evaluation

import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.sparql.types.QueryStructure
import dev.tesserakt.sparql.types.extractAllBindings

class QueryContextImpl(ast: QueryStructure): QueryContext {

    // note: this cannot be a read only list, as some add bindings during initialisation, such as repeating paths
    private val bindings = ast.body.extractAllBindings().mapTo(mutableListOf()) { it.name }
    private val bindingsLut = bindings.withIndex().associateTo(mutableMapOf()) { (i, value) -> value to i }

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
