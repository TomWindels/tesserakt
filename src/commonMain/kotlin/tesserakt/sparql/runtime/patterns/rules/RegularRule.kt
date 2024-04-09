package tesserakt.sparql.runtime.patterns.rules

import tesserakt.rdf.types.Triple
import tesserakt.sparql.runtime.types.Bindings
import tesserakt.sparql.runtime.types.PatternASTr
import tesserakt.util.compatibleWith

// TODO: make 4 versions of this rule, alternating between exact and binding types for `s`, `o`
internal data class RegularRule(
    private val s: PatternASTr.Subject,
    private val p: PatternASTr.NonRepeatingPredicate,
    private val o: PatternASTr.Object
) : QueryRule<MutableList<Bindings>>() {

    /**
     * Inserts the provided triple as a set of valid bindings, and returns these bindings for use
     *  in expanding binding searches
     */
    fun matchAndInsert(triple: Triple, data: MutableList<Bindings>): Bindings? {
        if (!s.matches(triple.s) || !p.matches(triple.p) || !o.matches(triple.o)) {
            return null
        }
        // checking to see if there's any matches with the given triple
        val match = buildMap {
            s.bindingName?.let { name -> put(name, triple.s) }
            p.bindingName?.let { name -> put(name, triple.p) }
            o.bindingName?.let { name -> put(name, triple.o) }
        }
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

}
