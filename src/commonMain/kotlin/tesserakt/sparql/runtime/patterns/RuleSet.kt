package tesserakt.sparql.runtime.patterns

import tesserakt.rdf.types.Triple
import tesserakt.sparql.compiler.types.Pattern
import tesserakt.sparql.compiler.types.Patterns
import tesserakt.sparql.runtime.patterns.rules.QueryRule
import tesserakt.sparql.runtime.patterns.rules.RegularRule
import tesserakt.sparql.runtime.patterns.rules.repeating.FixedPredicateRepeatingRule
import tesserakt.sparql.runtime.patterns.rules.repeating.ZeroOrMoreFixedPredicateRepeatingRule
import tesserakt.sparql.runtime.types.Bindings

internal class RuleSet (rules: List<QueryRule<*>>) {

    // TODO: make processing these rules in state their own separate type, so further specializing the various
    //  rule types can be done in separate collections for more optimal V-Table usage
    private val regular = rules.filterIsInstance<RegularRule>()
    private val exactRepeating = rules.filterIsInstance<FixedPredicateRepeatingRule>()

    inner class State {

        private val regularData = Array(regular.size) { regular[it].newState() }
        private val exactRepeatingData = Array(exactRepeating.size) { exactRepeating[it].newState() }

        fun process(triple: Triple): List<Bindings> {
            // checking all regular rules to see which match with this triple exactly
            val regularSatisfied = mutableSetOf<Int>()
            val repeatingSatisfied = mutableSetOf<Int>()
            val constraints = mutableMapOf<String, Triple.Term>()
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
                        repeatingSatisfied.add(i)
                        repeatingResults.addAll(new)
                    }
                }
                repeatingResults
            } else {
                // simply adding the resulting new paths, if any
                exactRepeating.forEachIndexed { i, rule -> rule.quickInsert(triple, exactRepeatingData[i]) }
                listOf(constraints)
            }
            if (constraints.isEmpty() && results.isEmpty()) {
                return emptyList()
            }
            // now all possible versions through repetitions are created
            for (ruleId in exactRepeating.indices - repeatingSatisfied) {
                results = exactRepeating[ruleId].expand(results, exactRepeatingData[ruleId])
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

        fun from(patterns: Patterns) =
            RuleSet(rules = patterns.toFilterRules())

        /* helpers */

        private fun Patterns.toFilterRules(): List<QueryRule<*>> = buildList {
            // always incrementing the blank id with the number of new pattern id's to make sure there's no
            //  incorrect overlap happening between rules
            var blankId = 0
            this@toFilterRules.forEach { pattern ->
                val new = pattern.toFilterRules(id = blankId)
                blankId += new.size
                addAll(new)
            }
        }.distinct()

        private fun Pattern.toFilterRules(id: Int) = p.toFilterRules(s.asFilterElement(), o.asFilterElement(), id)

        private fun Pattern.Subject.asFilterElement(): QueryRule.Element {
            return when (this) {
                is Pattern.Binding -> QueryRule.Binding(name)
                is Pattern.Exact -> QueryRule.Exact(value)
            }
        }

        private fun Pattern.Object.asFilterElement(): QueryRule.Element {
            return when (this) {
                is Pattern.Binding -> QueryRule.Binding(name)
                is Pattern.Exact -> QueryRule.Exact(value)
            }
        }

        private fun Pattern.Predicate.toFilterRules(
            s: QueryRule.Element,
            o: QueryRule.Element,
            id: Int
        ): List<QueryRule<*>> = when (this@toFilterRules) {
            is Pattern.Binding -> listOf(RegularRule(s, QueryRule.Binding(name), o))
            is Pattern.Exact -> listOf(RegularRule(s, QueryRule.Exact(value), o))
            is Pattern.Not -> listOf(RegularRule(s, QueryRule.Inverse(predicate.toFilterElement() as QueryRule.FixedPredicate), o))
            is Pattern.ZeroOrMore -> listOf(ZeroOrMoreFixedPredicateRepeatingRule(s as QueryRule.Binding, value.toFilterElement() as QueryRule.FixedPredicate, o as QueryRule.Binding))
            is Pattern.Chain -> generateRules(s, o, id)
            is Pattern.Constrained -> listOf(RegularRule(s, QueryRule.Either(allowed.map { it.toFilterElement() as QueryRule.FixedPredicate }), o))
        }

        private fun Pattern.Predicate.toFilterElement(): QueryRule.Predicate = when (this@toFilterElement) {
            is Pattern.Binding -> QueryRule.Binding(name)
            is Pattern.Exact -> QueryRule.Exact(value)
            is Pattern.Not -> QueryRule.Inverse(predicate.toFilterElement() as QueryRule.FixedPredicate)
            is Pattern.Constrained -> QueryRule.Either(allowed.map { it.toFilterElement() as QueryRule.FixedPredicate })
            // illegal, these cannot be used in regular query rules
            is Pattern.Chain -> throw IllegalStateException()
            is Pattern.ZeroOrMore -> throw IllegalStateException()
        }

        private fun Pattern.Chain.generateRules(
            s: QueryRule.Element,
            o: QueryRule.Element,
            id: Int
        ): List<QueryRule<*>> = buildList {
            var start = s
            var blank = id
            for (i in 0 ..< (list.size - 1)) {
                // setting the intermediate object
                val end = QueryRule.Binding("_q_b${blank++}")
                // adding the current iteration
                addAll(list[i].toFilterRules(start, end, id + size))
                // moving the subject
                start = end
            }
            // adding the final one, pointing to the object
            add(RegularRule(start, list.last().toFilterElement(), o))
        }

    }

}
