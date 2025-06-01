package dev.tesserakt.sparql.runtime.evaluation.context

import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.sparql.runtime.evaluation.BindingIdentifier
import dev.tesserakt.sparql.runtime.evaluation.TermIdentifier
import dev.tesserakt.sparql.runtime.evaluation.mapping.IntPairMapping
import dev.tesserakt.sparql.runtime.evaluation.mapping.Mapping

object GlobalQueryContext: QueryContext {

    private val bindings = mutableListOf<String>()
    private val bindingsLut = mutableMapOf<String, Int>()

    private val terms = mutableMapOf<Quad.Element, Int>()
    // as terms are never removed from an active context, we can keep it as a regular list without risking IDs
    // shifting over
    private val termsLut = mutableListOf<Quad.Element>()

    override fun resolveBinding(value: String): Int {
        return bindingsLut.getOrPut(value) {
            val i = bindings.size
            require(bindingsLut.size == i)
            bindings.add(value)
            i
        }
    }

    override fun resolveTerm(value: Quad.Element): Int {
        return terms.getOrPut(value) {
            val i = terms.size
            termsLut.add(value)
            i
        }
    }

    override fun resolveBinding(id: Int): String {
        return bindings[id]
    }

    override fun resolveTerm(id: Int): Quad.Element {
        return termsLut[id]
    }

    override fun create(terms: Iterable<Pair<String, Quad.Element>>): IntPairMapping =
        IntPairMapping(this, terms)

    override fun createFromIdentifiers(terms: Iterable<Pair<BindingIdentifier, TermIdentifier>>): Mapping =
        IntPairMapping(terms)

    override fun emptyMapping(): IntPairMapping = IntPairMapping.EMPTY

}
