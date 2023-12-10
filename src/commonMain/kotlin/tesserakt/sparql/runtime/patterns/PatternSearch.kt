package tesserakt.sparql.runtime.patterns

import tesserakt.rdf.types.Triple
import tesserakt.sparql.runtime.types.Bindings
import tesserakt.util.associateIndexedNotNull
import tesserakt.util.merge

internal class PatternSearch(
    private val ruleSet: RuleSet
) {

    // contains for every rule [dim 0] a growing collection [dim 1] of bindings [dim 2] that have satisfied that rule
    private val patternData = Array<MutableList<Bindings>>(ruleSet.rules.size) { mutableListOf() }
    // an accompanying collection (same dims) representing the triple index, so duplicate paths are avoided during
    //  answer discovery
    private val patternSources = Array<MutableList<Int>>(ruleSet.rules.size) { mutableListOf() }
    private var tripleIndex = 0

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
            results.addAll(getResultsFor(constraints = constraints, pending = ruleSet.rules.indices.toSet() - satisfied))
        }
        // with the results finalized, the triple bindings can be added to the bindings
        // TODO: this can be optimised by reusing stuff from the logic above
        for (i in ruleSet.rules.indices) {
            val match = matches[i] ?: continue
            patternData[i].add(
                ruleSet.rules[i].bindings.associateIndexedNotNull { j, name -> name?.let { it to match[j]!! } }
            )
            // also marking it as the source in the accompanying sources collection
            patternSources[i].add(tripleIndex)
        }
        // incrementing triple index for next use
        ++tripleIndex
        return results
    }

    /* helpers */

    // return value is of shape [ rules.count, 3 ], with the 3 elements representing the bound value
    //  for that rule, if any, if found
    private fun Triple.extractRuleBindings(): Array<Array<Triple.Term?>?> =
        Array(ruleSet.rules.size) { ruleSet.rules[it].process(this) }

    /**
     * Finds all results with the given constraints for binding values, enforcing the provided set of rules
     */
    private fun getResultsFor(
        constraints: Bindings,
        pending: Set<Int>,
    ): List<Bindings> {
        // FIXME double firing for the `chain` case
        // going through all options
        val result = mutableListOf<Bindings>()
        // if only a single rule is required, the matches for this single rule get sent back directly
        if (pending.size == 1) {
            val results = patternData[pending.first()].matching(constraints)
            // end has been reached, so these new constraints are the results
            results.forEach { c ->
                result.add(c + constraints)
            }
            return result
        }
        // otherwise, multiple rules are still required, so iteratively going through them (as taking a different
        //  order of applying rules might create new results) and recursing through the remaining set of rules
        for (ruleId in pending) {
            val results = patternData[ruleId].matching(constraints)
            if (results.isEmpty()) {
                continue
            }
            val newRules = pending - ruleId
            results.forEach { c ->
                // merging the resulting constraint with the initial set of constraints, and recursively
                //  continuing with these new constraints
                result.addAll(getResultsFor(c + constraints, newRules))
            }
        }
        return result
    }

    private inline fun Iterable<Bindings>.matching(constraints: Bindings) =
        filter { source -> constraints.all { (key, value) -> key !in source || source[key] == value } }

}
