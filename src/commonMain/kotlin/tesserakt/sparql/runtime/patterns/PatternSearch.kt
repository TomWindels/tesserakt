package tesserakt.sparql.runtime.patterns

import tesserakt.rdf.types.Triple
import tesserakt.sparql.runtime.patterns.PatternSearch.State.Companion.newState
import tesserakt.sparql.runtime.types.Bindings
import tesserakt.util.associateIndexedNotNull
import tesserakt.util.merge
import kotlin.jvm.JvmInline

internal class PatternSearch(
    private val ruleSet: RuleSet,
    private val state: State = ruleSet.newState()
) {

    @JvmInline
    value class State internal constructor(
        // collection [dim 1] of bindings [dim 2] that have satisfied rule at the given index [dim 0]
        val data: List<MutableList<Bindings>>
    ) {

        companion object {
            // all results associated per rule; rules.size == candidates.size
            fun RuleSet.newState() = State(data = List(rules.size) { mutableListOf() })
        }

    }

    /**
     * Processes the given triple using the given `state`, returns any valid bindings created since processing
     *  the triple (typically 0 .. 1 map)
     */
    fun process(triple: Triple): MutableList<Bindings> {
        val results = mutableListOf<Bindings>()
        // extracting all bindings from the given triple, calculating all results with constraints created from
        //  this triple by recursively going through past results, until all rules are satisfied, rest of the data
        //  filled in from state cache, and **then** add it to that cache
        val matches = triple.extractRuleBindings()
        // if no matches are found, nothing can be done further
        if (matches.none { it != null }) {
            return results
        }
        // extracting all satisfied rules, and skipping them during the search
        val satisfied = matches.mapIndexedNotNull { i, data -> i.takeIf { data != null } }.toSet()
        // merging all data from the matches as constraints for the next search
        // TODO: this can be optimised, make this a dedicated function doing it in a single swoop
        val constraints = matches
            .mapIndexedNotNull { i, data ->
                data?.let {
                    ruleSet.rules[i].bindings.associateIndexedNotNull { i, name -> name?.let { it to data[i]!! } }
                }
            }
            .merge()
        if (satisfied.size == ruleSet.rules.size) {
            // all rules are satisfied, so constraints are the resulting bindings
            results.add(constraints)
        } else {
            // not all constraints are satisfied, so finding remaining values until they are
            results.addAll(findAllResultsInCacheWithConstraints(constraints = constraints, skipRules = satisfied))
        }
        // with the results finalized, the triple bindings can be added to the bindings
        // TODO: this can be optimised by reusing stuff from the logic above
        for (i in ruleSet.rules.indices) {
            val match = matches[i] ?: continue
            state.data[i].add(
                ruleSet.rules[i].bindings.associateIndexedNotNull { j, name -> name?.let { it to match[j]!! } }
            )
        }
        return results
    }

    /* helpers */

    // return value is of shape [ rules.count, 3 ], with the 3 elements representing the bound value
    //  for that rule, if any, if found
    private fun Triple.extractRuleBindings(): Array<Array<Triple.Term?>?> =
        Array(ruleSet.rules.size) { ruleSet.rules[it].process(this) }

    // finds all results with the given constraints for binding values, skipping the provided `skipRule` in the search
    private fun findAllResultsInCacheWithConstraints(
        constraints: Bindings,
        skipRules: Set<Int>
    ): List<Bindings> {
        // FIXME double firing for the `chain` case
        // going through all options
        val result = mutableListOf<Bindings>()
        for (ruleId in ruleSet.rules.indices - skipRules) {
            val results = state.data[ruleId].matching(constraints)
            if (results.isEmpty()) {
                continue
            }
            if (skipRules.size == ruleSet.rules.size - 1) {
                // end has been reached, so these new constraints are the results
                result.addAll(results.map { c -> c + constraints })
            } else {
                // creating new constrains for every item in the result
                val newConstraints = results.map { c -> c + constraints }
                // recursively going through them
                newConstraints.forEach { c ->
                    result.addAll(findAllResultsInCacheWithConstraints(c, skipRules + ruleId))
                }
            }
        }
        return result
    }

    private inline fun Iterable<Bindings>.matching(constraints: Bindings) =
        filter { source -> constraints.all { (key, value) -> key !in source || source[key] == value } }

}
