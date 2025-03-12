package dev.tesserakt.sparql.types

import dev.tesserakt.rdf.types.Quad


/* helpers for using the pattern element types */

val TriplePattern.Subject.bindingName: String?
    get() = (this as? TriplePattern.Binding)?.name

val TriplePattern.Object.bindingName: String?
    get() = (this as? TriplePattern.Binding)?.name

val TriplePattern.Predicate.bindingName: String?
    get() = (this as? TriplePattern.Binding)?.name

fun TriplePattern.Subject.matches(term: Quad.Term): Boolean =
    (this !is TriplePattern.Exact || this.term == term)

fun TriplePattern.Object.matches(term: Quad.Term): Boolean =
    (this !is TriplePattern.Exact || this.term == term)

fun TriplePattern.Exact.matches(term: Quad.Term): Boolean =
    term == this.term

fun TriplePattern.Predicate.matches(term: Quad.Term): Boolean = when (this) {
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

fun GraphPattern.extractAllBindings(): List<TriplePattern.Binding> =
    (
            patterns.flatMap { pattern -> pattern.extractAllBindings() } +
                    unions.flatMap { union -> union.flatMap { it.extractAllBindings() } } +
                    optional.flatMap { optional -> optional.segment.extractAllBindings() }
            ).distinct()

fun Segment.extractAllBindings() = when (this) {
    is SelectQuerySegment -> query.extractAllOutputsAsBindings()
    is GraphPatternSegment -> pattern.extractAllBindings()
}

fun SelectQueryStructure.extractAllOutputsAsBindings() =
    output?.map { TriplePattern.NamedBinding(it.name) } ?: emptyList()

fun TriplePattern.extractAllBindings(): List<TriplePattern.Binding> {
    val result = mutableListOf<TriplePattern.Binding>()
    when (s) {
        is TriplePattern.Binding -> result.add(s)
        is TriplePattern.Exact -> { /* nothing to do */ }
    }
    result.addAll(p.extractAllBindings())
    result.addAll(o.extractAllBindings())
    return when (result.size) {
        0 -> emptyList()
        else -> result
    }
}

// helper for the helper

private fun TriplePattern.Predicate.extractAllBindings(): List<TriplePattern.Binding> {
    return when (this) {
        is TriplePattern.Sequence -> chain.flatMap { it.extractAllBindings() }
        is TriplePattern.UnboundSequence -> chain.flatMap { it.extractAllBindings() }
        is TriplePattern.Alts -> allowed.flatMap { it.extractAllBindings() }
        is TriplePattern.SimpleAlts -> allowed.flatMap { it.extractAllBindings() }
        is TriplePattern.Binding -> listOf(this)
        is TriplePattern.Exact -> emptyList()
        is TriplePattern.Negated -> terms.extractAllBindings()
        is TriplePattern.ZeroOrMore -> element.extractAllBindings()
        is TriplePattern.OneOrMore -> element.extractAllBindings()
    }
}

private fun TriplePattern.Object.extractAllBindings(): List<TriplePattern.Binding> = when (this) {
    is TriplePattern.Binding -> listOf(this)
    is TriplePattern.Exact -> { emptyList() }
}
