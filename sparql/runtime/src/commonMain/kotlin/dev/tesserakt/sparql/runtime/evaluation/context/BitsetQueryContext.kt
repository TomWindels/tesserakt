package dev.tesserakt.sparql.runtime.evaluation.context

import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.sparql.runtime.evaluation.BindingIdentifier
import dev.tesserakt.sparql.runtime.evaluation.TermIdentifier
import dev.tesserakt.sparql.runtime.evaluation.mapping.BitsetMapping
import dev.tesserakt.sparql.types.QueryStructure

class BitsetQueryContext(ast: QueryStructure): QueryContext {

    private val bindings = BindingsContext(ast)

    private val terms = mutableMapOf<Quad.Element, Int>()
    // as terms are never removed from an active context, we can keep it as a regular list without risking IDs
    // shifting over
    private val termsLut = mutableListOf<Quad.Element>()

    override fun newAnonymousBinding(): Int {
        return bindings.newAnonymousBinding()
    }

    override fun resolveBinding(value: String): Int {
        return bindings.encode(value)
    }

    override fun resolveTerm(value: Quad.Element): Int {
        return terms.getOrPut(value) {
            val i = terms.size
            termsLut.add(value)
            i
        }
    }

    override fun resolveBinding(id: Int): String {
        return bindings.decode(id)
    }

    override fun resolveTerm(id: Int): Quad.Element {
        return termsLut[id]
    }

    override fun mappingFromValues(terms: Iterable<Pair<String, Quad.Element>>): BitsetMapping {
        return BitsetMapping(this, terms)
    }

    override fun mappingFromIdentifiers(terms: Iterable<Pair<BindingIdentifier, TermIdentifier>>): BitsetMapping {
        return BitsetMapping(terms)
    }

    override fun emptyMapping(): BitsetMapping {
        return BitsetMapping.EMPTY
    }

}
