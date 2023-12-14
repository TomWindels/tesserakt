package tesserakt.sparql.runtime.patterns

import tesserakt.rdf.types.Triple
import tesserakt.sparql.runtime.patterns.QueryRule.Element.Companion.insert
import tesserakt.sparql.runtime.patterns.QueryRule.Element.Companion.matches
import tesserakt.sparql.runtime.types.Bindings
import tesserakt.util.compatibleWith

// TODO: make 4 versions of this rule, alternating between exact and binding types for `s`, `o`
internal data class RegularRule(
    private val s: Element,
    private val p: Element.Predicate,
    private val o: Element
) : QueryRule<MutableList<Bindings>>() {

    /**
     * Inserts the provided triple as a set of valid bindings, and returns these bindings for use
     *  in expanding binding searches
     */
    fun matchAndInsert(triple: Triple, data: MutableList<Bindings>): Bindings? {
        // checking to see if there's any matches with the given triple
        val match = process(triple) ?: return null
        // adding it to the growing collection of data
        data.add(match)
        return match
    }

    fun expand(input: Bindings, data: MutableList<Bindings>): List<Bindings> {
        return data.mapNotNull { previous ->
            // checking to see if there's any incompatibility in the input constraints
            if (input.compatibleWith(previous)) {
                input + previous
            } else {
                null
            }
        }
    }

    override fun newState(): MutableList<Bindings> = mutableListOf()

    /**
     * Processes the incoming triple, returns `null` if no match is found, otherwise a map containing
     *  all pairs of binding name and binding value
     */
    private fun process(triple: Triple): Bindings? {
        return if (
            !s.matches(triple.s) ||
            !p.matches(triple.p) ||
            !o.matches(triple.o)
        ) {
            null
        } else {
            val result = mutableMapOf<String, Triple.Term>()
            result.insert(s, triple.s)
            result.insert(p, triple.p)
            result.insert(o, triple.o)
            return result
        }
    }

}
