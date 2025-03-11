package dev.tesserakt.sparql.ast

import dev.tesserakt.rdf.types.Quad


/* helpers for using the pattern element types */

internal val TriplePattern.Subject.bindingName: String?
    get() = (this as? TriplePattern.Binding)?.name

internal val TriplePattern.Object.bindingName: String?
    get() = (this as? TriplePattern.Binding)?.name

internal val TriplePattern.Predicate.bindingName: String?
    get() = (this as? TriplePattern.Binding)?.name

internal fun TriplePattern.Subject.matches(term: Quad.Term): Boolean =
    (this !is TriplePattern.Exact || this.term == term)

internal fun TriplePattern.Object.matches(term: Quad.Term): Boolean =
    (this !is TriplePattern.Exact || this.term == term)

internal fun TriplePattern.Exact.matches(term: Quad.Term): Boolean =
    term == this.term

internal fun TriplePattern.Predicate.matches(term: Quad.Term): Boolean = when (this) {
    /* all of these contain a binding, so automatically, it matches any term */
    is TriplePattern.NamedBinding -> true
    is TriplePattern.Alts -> true
    is TriplePattern.GeneratedBinding -> true
    /* all of these match only a subset of terms, so checking manually */
    is TriplePattern.Exact -> term == this.term
    is TriplePattern.SimpleAlts -> allowed.any { it.matches(term) }
    is TriplePattern.Negated -> !terms.matches(term)
    /* these cannot be directly matched to terms, so bailing */
    is TriplePattern.Sequence,
    is TriplePattern.UnboundSequence -> throw IllegalArgumentException("Sequences cannot be directly matched with terms!")
    is TriplePattern.OneOrMore,
    is TriplePattern.ZeroOrMore -> throw IllegalArgumentException("Repeating patterns cannot be directly matched with terms!")
}
