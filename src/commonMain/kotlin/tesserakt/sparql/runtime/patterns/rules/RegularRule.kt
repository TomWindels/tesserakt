package tesserakt.sparql.runtime.patterns.rules

import tesserakt.rdf.types.Triple
import tesserakt.sparql.runtime.types.Bindings
import tesserakt.util.compatibleWith

// TODO: make 4 versions of this rule, alternating between exact and binding types for `s`, `o`
internal data class RegularRule(
    private val s: Element,
    private val p: Predicate,
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

    fun expand(input: List<Bindings>, data: MutableList<Bindings>): List<Bindings> {
        return input.flatMap { bindings ->
            data.mapNotNull { previous ->
                // checking to see if there's any incompatibility in the input constraints
                if (bindings.compatibleWith(previous)) {
                    bindings + previous
                } else {
                    null
                }
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
            s.bindingName?.let { name -> result.put(name, triple.s) }
            p.bindingName?.let { name -> result.put(name, triple.p) }
            o.bindingName?.let { name -> result.put(name, triple.o) }
            return result
        }
    }

}
