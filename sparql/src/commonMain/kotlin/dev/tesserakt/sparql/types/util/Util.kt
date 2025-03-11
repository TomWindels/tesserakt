package dev.tesserakt.sparql.types.util

import dev.tesserakt.sparql.types.runtime.element.*

internal val ZeroCardinality = Cardinality(0)

internal val OneCardinality = Cardinality(1)

fun Query.QueryBody.getAllNamedBindings(): Set<Pattern.NamedBinding> =
    buildSet {
        addAll(patterns.getAllNamedBindings())
        optional.forEach { optional -> addAll(optional.segment.getAllNamedBindings()) }
        unions.forEach { union -> union.forEach { segment -> addAll(segment.getAllNamedBindings()) } }
    }

/**
 * Extracts all named bindings from the original query (excluding generated ones from the AST)
 */
fun Patterns.getAllNamedBindings(): Set<Pattern.NamedBinding> =
    buildSet {
        this@getAllNamedBindings.forEach { pattern ->
            addAll(pattern.getAllNamedBindings())
        }
    }

fun Pattern.getAllNamedBindings(): Set<Pattern.NamedBinding> {
    return if (
        s !is Pattern.NamedBinding &&
        p !is Pattern.NamedBinding &&
        o !is Pattern.NamedBinding
    ) {
        emptySet()
    } else buildSet {
        (s as? Pattern.NamedBinding)?.let { add(it) }
        p.getNamedBinding()?.let { add(it) }
        (o as? Pattern.NamedBinding)?.let { add(it) }
    }
}

private fun Segment.getAllNamedBindings(): Set<Pattern.NamedBinding> {
    return when (this) {
        is SelectQuerySegment ->
            query.bindings.mapTo(mutableSetOf()) { Pattern.NamedBinding(it) }

        is StatementsSegment ->
            statements.getAllNamedBindings()
    }
}

private fun Pattern.Predicate.getNamedBinding(): Pattern.NamedBinding? = when (this) {
    is Pattern.Alts,
    is Pattern.Exact,
    is Pattern.GeneratedBinding,
    is Pattern.Sequence,
    is Pattern.SimpleAlts,
    is Pattern.UnboundSequence,
    is Pattern.Negated,
    is Pattern.OneOrMore,
    is Pattern.ZeroOrMore -> null
    is Pattern.NamedBinding -> this
}
