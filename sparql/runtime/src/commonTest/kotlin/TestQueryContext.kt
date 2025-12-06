
import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.sparql.runtime.evaluation.context.QueryContext
import dev.tesserakt.sparql.runtime.evaluation.mapping.IntPairMapping

class TestQueryContext: QueryContext {

    // note: this cannot be a read only list, as some add bindings during initialisation, such as repeating paths
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

    override fun create(terms: Iterable<Pair<String, Quad.Element>>): IntPairMapping {
        return IntPairMapping(this, terms)
    }

    override fun emptyMapping(): IntPairMapping {
        return IntPairMapping.EMPTY
    }

}
