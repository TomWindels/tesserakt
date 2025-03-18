package sparql.tests

import dev.tesserakt.sparql.types.*
import dev.tesserakt.testing.Test
import dev.tesserakt.testing.TestFilter
import sparql.types.QueryExecutionTest

object DefaultTestFiltering: TestFilter {

    override fun shouldSkip(test: Test): Boolean {
        if (test !is QueryExecutionTest) {
            return true
        }
        val structure = test.structure
        return shouldSkip(structure)
    }

    private fun shouldSkip(structure: QueryStructure): Boolean {
        return structure.body.has { it.p.containsRepeatingPredicate() }
    }

    fun GraphPattern.has(callback: (pattern: TriplePattern) -> Boolean): Boolean {
        if (patterns.any(callback)) {
            return true
        }
        filters.forEach { filter ->
            when (filter) {
                is Filter.Exists -> if (filter.pattern.has(callback)) {
                    return true
                }
                is Filter.NotExists -> if (filter.pattern.has(callback)) {
                    return true
                }
                else -> { /* nothing to do */ }
            }
        }
        unions.forEach { union ->
            union.segments.forEach { segment ->
                when (segment) {
                    is GraphPatternSegment -> if (segment.pattern.has(callback)) {
                        return true
                    }
                    is SelectQuerySegment -> if (segment.query.body.has(callback)) {
                        return true
                    }
                }
            }
        }
        return false
    }

    private fun TriplePattern.Predicate.containsRepeatingPredicate(): Boolean {
        return when (this) {
            is TriplePattern.OneOrMore -> true
            is TriplePattern.ZeroOrMore -> true
            is TriplePattern.UnboundSequence -> chain.any { it.containsRepeatingPredicate() }
            is TriplePattern.Sequence -> chain.any { it.containsRepeatingPredicate() }
            is TriplePattern.Alts -> allowed.any { it.containsRepeatingPredicate() }
            is TriplePattern.SimpleAlts -> false
            is TriplePattern.Negated -> false
            is TriplePattern.Exact -> false
            is TriplePattern.Binding -> false
        }
    }

}
