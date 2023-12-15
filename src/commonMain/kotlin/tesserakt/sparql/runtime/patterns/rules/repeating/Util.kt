package tesserakt.sparql.runtime.patterns.rules.repeating

import tesserakt.sparql.runtime.patterns.rules.QueryRule

internal fun zeroOrMoreRepeatingOf(
    s: QueryRule.Element,
    p: QueryRule.Predicate,
    o: QueryRule.Element
): RepeatingRule<*> = when {
    s is QueryRule.Binding && o is QueryRule.Binding -> when (p) {
        is QueryRule.Binding -> ZeroOrMoreBindingPredicateRepeatingRule(s, p, o)
        is QueryRule.FixedPredicate -> ZeroOrMoreFixedPredicateRepeatingRule(s, p, o)
    }
    else -> throw UnsupportedOperationException("Using fixed s/o terms in repeating rules is currently unsupported!")
}

internal fun oneOrMoreRepeatingOf(
    s: QueryRule.Element,
    p: QueryRule.Predicate,
    o: QueryRule.Element
): RepeatingRule<*> = when {
    s is QueryRule.Binding && o is QueryRule.Binding -> when (p) {
        is QueryRule.Binding -> OneOrMoreBindingPredicateRepeatingRule(s, p, o)
        is QueryRule.FixedPredicate -> OneOrMoreFixedPredicateRepeatingRule(s, p, o)
    }
    else -> throw UnsupportedOperationException("Using fixed s/o terms in repeating rules is currently unsupported!")
}
