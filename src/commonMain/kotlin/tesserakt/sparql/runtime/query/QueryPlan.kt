package tesserakt.sparql.runtime.query

import tesserakt.rdf.types.Triple
import tesserakt.sparql.compiler.types.Pattern
import tesserakt.sparql.compiler.types.Patterns
import tesserakt.sparql.runtime.query.QueryPlan.State.Companion.newState
import tesserakt.sparql.runtime.types.Bindings
import tesserakt.util.associateIndexedNotNull
import kotlin.jvm.JvmInline

internal class QueryPlan(patterns: Patterns) {

    sealed interface QueryElement {

        @JvmInline
        value class Exact(val term: Triple.Term): QueryElement

        @JvmInline
        value class Binding(val name: String): QueryElement

    }

    data class QueryRule(
        val s: QueryElement,
        val p: QueryElement,
        val o: QueryElement
    ) {

        private val constraints = listOf(
            (s as? QueryElement.Exact)?.term,
            (p as? QueryElement.Exact)?.term,
            (o as? QueryElement.Exact)?.term
        )

        internal val bindings = listOf(
            (s as? QueryElement.Binding)?.name,
            (p as? QueryElement.Binding)?.name,
            (o as? QueryElement.Binding)?.name
        )

        /**
         * Processes the incoming triple, returns `null` if no match is found, otherwise an array of size 3
         *  with the bounded values (if any)
         */
        internal fun process(triple: Triple): Array<Triple.Term?>? {
            if (!fits(triple)) {
                return null
            }
            // FIXME: bindings is currently only used here, so can be optimised
            return Array(3) { i -> triple[i].takeIf { bindings[i] != null } }
        }

        /** Checks if any `Exact` matches aren't satisfied (meaning the triple doesn't apply here) **/
        internal fun fits(triple: Triple): Boolean {
            constraints.forEachIndexed { i, constraint ->
                if (constraint != null && constraint != triple[i]) {
                    return false
                }
            }
            return true
        }
    }

    data class RuleSet(
        val rules: List<QueryRule>
    ) {

        init {
            println(rules)
        }

        companion object {

            fun from(patterns: Patterns) =
                RuleSet(rules = patterns.flatMap { it.toFilterRules() }.distinct())

            /* helpers */

            private fun List<QueryRule>.extractBindingNames(): Set<String> = buildSet {
                this@extractBindingNames.forEach {  (s, p, o) ->
                    if (s is QueryElement.Binding) { add(s.name) }
                    if (p is QueryElement.Binding) { add(p.name) }
                    if (o is QueryElement.Binding) { add(o.name) }
                }
            }

            private fun QueryRule.extractBindingNames(): Array<String?> =
                arrayOf(
                    (s as? QueryElement.Binding)?.name,
                    (p as? QueryElement.Binding)?.name,
                    (o as? QueryElement.Binding)?.name
                )

        }

//        // all bindings present in the rule set, used to detect completed state items
//        private val bindings = rules.extractBindingNames()
        // all bindings present per rule, used to extract results from the set of retrieved bindings
//        private val bindings = Array(rules.size) { index -> rules[index].extractBindingNames() }

        /**
         * Processes the given triple using the given `state`, returns any valid bindings created since processing
         *  the triple (typically 0 .. 1 map)
         */
        fun process(state: State, triple: Triple): MutableList<Bindings> {
//            rules.forEachIndexed { i, rule ->
//                val result = rule.process(triple)
//                if (result != null) {
//                    state.candidates[i].add(result)
//                }
//            }
//            return state.extractCompletedResults()
            val results = mutableListOf<Bindings>()
            // extracting all bindings from the given triple, calculating all results with constraints created from
            //  this triple by recursively going through past results, until all rules are satisfied, rest of the data
            //  filled in from state cache, and **then** add it to that cache

            // TODO: map these to the binding names directly, as this format is used twice (in both `for`s)
            val matches = triple.extractRuleBindings()
            // going through every match, but only if not null
            var ruleId = -1
            for (match in matches) {
                ++ruleId
                match ?: continue
                // using these matches to find all results in the cache
                val constraints = rules[ruleId].bindings
                    .associateIndexedNotNull { i, name -> name?.let { it to match[i]!! } }
                results.addAll(findAllResultsInCacheWithConstraints(state = state, constraints = constraints, skipRule = ruleId))
            }
            // with the results finalized, the triple bindings can be added to the bindings
            for (i in rules.indices) {
                val match = matches[i] ?: continue
                state.cache[i].add(
                    rules[i].bindings.associateIndexedNotNull { j, name -> name?.let { it to match[j]!! } }
                )
            }
            return results
        }

        /* helpers */

        // return value is of shape [ rules.count, 3 ], with the 3 elements representing the bound value
        //  for that rule, if any, if found
        private fun Triple.extractRuleBindings(): Array<Array<Triple.Term?>?> {
            val result = Array<Array<Triple.Term?>?>(rules.size) { null }
            rules.forEachIndexed { i, rule -> result[i] = rule.process(this) }
            return result
        }

        private fun findAllResultsInCacheWithConstraints(
            state: State,
            constraints: Bindings,
            skipRule: Int
        ): List<Bindings> {
            return if (rules.size == 1) {
                listOf(constraints)
            } else {
                // recursively going through the options
                findAllResultsInCacheWithConstraints(
                    state = state,
                    constraints = constraints,
                    skipRules = setOf(skipRule)
                )
            }
        }

        // finds all results with the given constraints for binding values, skipping the provided `skipRule` in the search
        private fun findAllResultsInCacheWithConstraints(
            state: State,
            constraints: Bindings,
            skipRules: Set<Int>
        ): List<Bindings> {
            // FIXME double firing for the `chain` case
            // going through all options
            val result = mutableListOf<Bindings>()
            for (ruleId in rules.indices - skipRules) {
                val results = state.cache[ruleId].matching(constraints)
                if (results.isEmpty()) {
                    continue
                }
                if (skipRules.size == rules.size - 1) {
                    // end has been reached, so these new constraints are the results
                    result.addAll(results.map { c -> c + constraints })
                } else {
                    // creating new constrains for every item in the result
                    val newConstraints = results.map { c -> c + constraints }
                    // recursively going through them
                    newConstraints.forEach { c ->
                        result.addAll(findAllResultsInCacheWithConstraints(state, c, skipRules + ruleId))
                    }
                }
            }
            return result
        }

        private inline fun Iterable<Bindings>.matching(constraints: Bindings) =
            filter { source -> constraints.all { (key, value) -> key !in source || source[key] == value } }

    }

    @JvmInline
    value class State private constructor(
        // a rule can have zero or more bindings associated with it, so it is a simple map
        //  a series of maps per rule, so list as upper type
        val cache: List<MutableList<Bindings>>
    ) {
        companion object {
            fun RuleSet.newState() =
                // all results associated per rule; rules.size == pending.size
                State(cache = List(rules.size) { mutableListOf() })
        }

    }

    companion object {

        /* helpers */

        private fun Pattern.toFilterRules(): List<QueryRule> = buildList {
            var subject = s.asFilterElement()
            var `object`: QueryElement
            // FIXME `blank` resetting for every predicate will cause overlap!
            var blank = 0
            val predicates = p.extractFilterElements()
            for (i in 0 until predicates.size - 1) {
                // setting the intermediate object
                `object` = QueryElement.Binding("q_b${blank++}")
                // adding the current iteration
                add(QueryRule(subject, predicates[i], `object`))
                // moving the subject
                subject = `object`
            }
            // adding the final one, pointing to the object
            add(QueryRule(subject, predicates.last(), o.asFilterElement()))
        }

        private fun Pattern.Subject.asFilterElement(): QueryElement {
            return when (this) {
                is Pattern.Binding -> QueryElement.Binding(name)
                is Pattern.Exact -> QueryElement.Exact(value)
            }
        }

        private fun Pattern.Object.asFilterElement(): QueryElement {
            return when (this) {
                is Pattern.Binding -> QueryElement.Binding(name)
                is Pattern.Exact -> QueryElement.Exact(value)
            }
        }

        private fun Pattern.Predicate.extractFilterElements(): List<QueryElement> = buildList {
            when (this@extractFilterElements) {
                is Pattern.Binding -> add(QueryElement.Binding(name))
                is Pattern.Exact -> add(QueryElement.Exact(value))
                is Pattern.Chain -> list.forEach { addAll(it.extractFilterElements()) }
                is Pattern.Constrained -> TODO()
                is Pattern.Not -> TODO()
                is Pattern.Repeating -> TODO()
            }
        }

    }

    private val rules = RuleSet.from(patterns = patterns)

    fun newState() = rules.newState()

    fun process(state: State, triple: Triple) = rules.process(state, triple)

}
