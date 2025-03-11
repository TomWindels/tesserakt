package dev.tesserakt.sparql.types.runtime.element

import dev.tesserakt.rdf.types.Quad


/* helpers for using the pattern element types */

internal val Pattern.Subject.bindingName: String?
    get() = (this as? Pattern.Binding)?.name

internal val Pattern.Object.bindingName: String?
    get() = (this as? Pattern.Binding)?.name

internal val Pattern.Predicate.bindingName: String?
    get() = (this as? Pattern.Binding)?.name

internal fun Pattern.Subject.matches(term: Quad.Term): Boolean =
    (this !is Pattern.Exact || this.term == term)

internal fun Pattern.Object.matches(term: Quad.Term): Boolean =
    (this !is Pattern.Exact || this.term == term)

internal fun Pattern.Exact.matches(term: Quad.Term): Boolean =
    term == this.term

internal fun Pattern.Predicate.matches(term: Quad.Term): Boolean = when (this) {
    /* all of these contain a binding, so automatically, it matches any term */
    is Pattern.NamedBinding -> true
    is Pattern.Alts -> true
    is Pattern.GeneratedBinding -> true
    /* all of these match only a subset of terms, so checking manually */
    is Pattern.Exact -> term == this.term
    is Pattern.SimpleAlts -> allowed.any { it.matches(term) }
    is Pattern.Negated -> !terms.matches(term)
    /* these cannot be directly matched to terms, so bailing */
    is Pattern.Sequence,
    is Pattern.UnboundSequence -> throw IllegalArgumentException("Sequences cannot be directly matched with terms!")
    is Pattern.OneOrMore,
    is Pattern.ZeroOrMore -> throw IllegalArgumentException("Repeating patterns cannot be directly matched with terms!")
}
