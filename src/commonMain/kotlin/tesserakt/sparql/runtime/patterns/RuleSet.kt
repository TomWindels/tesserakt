package tesserakt.sparql.runtime.patterns

import tesserakt.rdf.types.Triple
import tesserakt.sparql.compiler.types.Pattern
import tesserakt.sparql.compiler.types.Patterns
import tesserakt.sparql.runtime.types.Bindings

internal class RuleSet(
    val regular: List<RegularRule>,
    val repeating: List<RepeatingRule>
) {

    constructor(rules: List<QueryRule<*>>): this(
        regular = rules.filterIsInstance<RegularRule>(),
        repeating = rules.filterIsInstance<RepeatingRule>()
    )

    inner class State {

        private val regularData = Array(regular.size) { regular[it].newState() }
        private val repeatingData = Array(repeating.size) { repeating[it].newState() }

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
                repeating.forEachIndexed { i, rule ->
                    val new = rule.insertAndReturnNewPaths(triple, repeatingData[i])
                    if (new.isNotEmpty()) {
                        // rule has been satisfied, so marking it as such
                        repeatingSatisfied.add(i)
                        repeatingResults.addAll(new)
                    }
                }
                repeatingResults
            } else {
                // simply adding the resulting new paths, if any
                repeating.forEachIndexed { i, rule -> rule.quickInsert(triple, repeatingData[i]) }
                listOf(constraints)
            }
            if (constraints.isEmpty() && results.isEmpty()) {
                return emptyList()
            }
            // now all possible versions through repetitions are created
            for (ruleId in repeating.indices - repeatingSatisfied) {
                results = repeating[ruleId].expand(results, repeatingData[ruleId])
                if (results.isEmpty()) {
                    return emptyList()
                }
            }
            // now all intermediate results can get filtered with the other rules
            for (ruleId in regular.indices - regularSatisfied) {
                results = results.flatMap { c -> regular[ruleId].expand(c, regularData[ruleId]) }
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
                is Pattern.Binding -> QueryRule.Element.Binding(name)
                is Pattern.Exact -> QueryRule.Element.Exact(value)
            }
        }

        private fun Pattern.Object.asFilterElement(): QueryRule.Element {
            return when (this) {
                is Pattern.Binding -> QueryRule.Element.Binding(name)
                is Pattern.Exact -> QueryRule.Element.Exact(value)
            }
        }

        private fun Pattern.Predicate.toFilterRules(
            s: QueryRule.Element,
            o: QueryRule.Element,
            id: Int
        ): List<QueryRule<*>> = when (this@toFilterRules) {
            is Pattern.Binding -> listOf(RegularRule(s, toFilterElement(), o))
            is Pattern.Exact -> listOf(RegularRule(s, toFilterElement(), o))
            is Pattern.Not -> listOf(RegularRule(s, toFilterElement(), o))
            is Pattern.Repeating -> listOf(RepeatingRule(s as QueryRule.Element.Binding, value.toFilterElement() as QueryRule.Element.Exact, o as QueryRule.Element.Binding, optional = true /*FIXME*/ ))
            is Pattern.Chain -> generateRules(s, o, id)
            is Pattern.Constrained -> listOf(RegularRule(s, QueryRule.Element.Either(allowed.map { it.toFilterElement() as QueryRule.Element.Exact }), o))
        }

        private fun Pattern.Predicate.toFilterElement(): QueryRule.Element.Predicate = when (this@toFilterElement) {
            is Pattern.Binding -> QueryRule.Element.Binding(name)
            is Pattern.Exact -> QueryRule.Element.Exact(value)
            is Pattern.Not -> QueryRule.Element.Inverse((predicate as Pattern.Exact).value)
            is Pattern.Repeating -> throw IllegalStateException()
            // illegal, as these represent multiple rules or should be split up into unions
            is Pattern.Constrained -> throw IllegalStateException()
            is Pattern.Chain -> throw IllegalStateException()
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
                val end = QueryRule.Element.Binding("q_b${blank++}")
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
