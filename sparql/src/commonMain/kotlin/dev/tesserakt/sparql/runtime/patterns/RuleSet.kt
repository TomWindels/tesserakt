package dev.tesserakt.sparql.runtime.patterns

import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.sparql.runtime.patterns.rules.QueryRule
import dev.tesserakt.sparql.runtime.patterns.rules.RegularRule
import dev.tesserakt.sparql.runtime.patterns.rules.repeating.BindingPredicateRule
import dev.tesserakt.sparql.runtime.patterns.rules.repeating.FixedPredicateRule
import dev.tesserakt.sparql.runtime.patterns.rules.repeating.RepeatingRule.Companion.repeatingOf
import dev.tesserakt.sparql.runtime.types.Bindings
import dev.tesserakt.sparql.runtime.types.PatternASTr
import dev.tesserakt.sparql.runtime.types.PatternsASTr

internal class RuleSet (rules: List<QueryRule<*>>) {

    // TODO: make processing these rules in state their own separate type, so further specializing the various
    //  rule types can be done in separate collections for more optimal V-Table usage
    private val regular = rules.filterIsInstance<RegularRule>()
    private val exactRepeating = rules.filterIsInstance<FixedPredicateRule>()
    private val bindingRepeating = rules.filterIsInstance<BindingPredicateRule>()

    inner class State {

        private val regularData = Array(regular.size) { regular[it].newState() }
        private val exactRepeatingData = Array(exactRepeating.size) { exactRepeating[it].newState() }
        private val bindingRepeatingData = Array(bindingRepeating.size) { bindingRepeating[it].newState() }

        fun process(triple: Quad): List<Bindings> {
            // checking all regular rules to see which match with this triple exactly
            val regularSatisfied = mutableSetOf<Int>()
            val exactRepeatingSatisfied = mutableSetOf<Int>()
            val bindingRepeatingSatisfied = mutableSetOf<Int>()
            val constraints = mutableMapOf<String, Quad.Term>()
            regular.forEachIndexed { i, rule ->
                val match = rule.matchAndInsert(triple, regularData[i]) ?: return@forEachIndexed
                regularSatisfied.add(i)
                constraints += match
            }
            var results = if (constraints.isEmpty()) {
                val repeatingResults = mutableListOf<Bindings>()
                exactRepeating.forEachIndexed { i, rule ->
                    val new = rule.insertAndReturnNewPaths(triple, exactRepeatingData[i])
                    if (new.isNotEmpty()) {
                        // rule has been satisfied, so marking it as such
                        exactRepeatingSatisfied.add(i)
                        repeatingResults.addAll(new)
                    }
                }
                bindingRepeating.forEachIndexed { i, rule ->
                    val new = rule.insertAndReturnNewPaths(triple, bindingRepeatingData[i])
                    if (new.isNotEmpty()) {
                        // rule has been satisfied, so marking it as such
                        bindingRepeatingSatisfied.add(i)
                        repeatingResults.addAll(new)
                    }
                }
                repeatingResults
            } else {
                // simply adding the resulting new paths, if any
                exactRepeating.forEachIndexed { i, rule -> rule.quickInsert(triple, exactRepeatingData[i]) }
                bindingRepeating.forEachIndexed { i, rule -> rule.quickInsert(triple, bindingRepeatingData[i]) }
                listOf(constraints)
            }
            if (constraints.isEmpty() && results.isEmpty()) {
                return emptyList()
            }
            // now all possible versions through repetitions are created
            for (ruleId in exactRepeating.indices - exactRepeatingSatisfied) {
                results = exactRepeating[ruleId].expand(results, exactRepeatingData[ruleId])
                if (results.isEmpty()) {
                    return emptyList()
                }
            }
            for (ruleId in bindingRepeating.indices - bindingRepeatingSatisfied) {
                results = bindingRepeating[ruleId].expand(results, bindingRepeatingData[ruleId])
                if (results.isEmpty()) {
                    return emptyList()
                }
            }
            // now all intermediate results can get filtered with the other rules
            for (ruleId in regular.indices - regularSatisfied) {
                results = regular[ruleId].expand(results, regularData[ruleId])
                if (results.isEmpty()) {
                    return emptyList()
                }
            }
            return results
        }

    }

    companion object {

        fun from(patterns: PatternsASTr) =
            RuleSet(rules = patterns.toFilterRules())

        /* helpers */

        private fun PatternsASTr.toFilterRules(): List<QueryRule<*>> = map { (s, p, o) ->
            when (p) {
                is PatternASTr.RepeatingPredicate -> repeatingOf(s, p, o)
                is PatternASTr.NonRepeatingPredicate -> RegularRule(s, p, o)
            }
        }

    }

}
