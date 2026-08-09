package dev.tesserakt.sparql.util

import dev.tesserakt.sparql.types.*

val ZeroCardinality = Cardinality(0)

val OneCardinality = Cardinality(1)

fun GraphPattern.getAllNamedBindings(): Set<TriplePattern.NamedBinding> =
    buildSet {
        statements.forEach { statement ->
            when (statement) {
                is Optional -> addAll(statement.patterns.getAllNamedBindings())
                is TriplePattern -> addAll(statement.getAllNamedBindings())
                is Union -> addAll(statement.segments.getAllNamedBindings())
            }
        }
    }

/**
 * Extracts all named bindings from the original query (excluding generated ones from the AST)
 */
fun TriplePatternSet.getAllNamedBindings(): Set<TriplePattern.NamedBinding> =
    buildSet {
        this@getAllNamedBindings.forEach { pattern ->
            addAll(pattern.getAllNamedBindings())
        }
    }

fun TriplePattern.getAllNamedBindings(): Set<TriplePattern.NamedBinding> {
    return if (
        s !is TriplePattern.NamedBinding &&
        p !is TriplePattern.NamedBinding &&
        o !is TriplePattern.NamedBinding
    ) {
        emptySet()
    } else buildSet {
        (s as? TriplePattern.NamedBinding)?.let { add(it) }
        p.getNamedBinding()?.let { add(it) }
        (o as? TriplePattern.NamedBinding)?.let { add(it) }
    }
}

fun List<Segment>.getAllNamedBindings(): Set<TriplePattern.NamedBinding> {
    return flatMapTo(mutableSetOf()) { segment -> segment.getAllNamedBindings() }
}

private fun Segment.getAllNamedBindings(): Set<TriplePattern.NamedBinding> {
    return when (this) {
        is SelectQuerySegment ->
            query.bindings.mapTo(mutableSetOf()) { TriplePattern.NamedBinding(it) }

        is GraphPatternSegment ->
            pattern.getAllNamedBindings()
    }
}

private fun TriplePattern.Predicate.getNamedBinding(): TriplePattern.NamedBinding? = when (this) {
    is TriplePattern.Alts,
    is TriplePattern.Exact,
    is TriplePattern.GeneratedBinding,
    is TriplePattern.Sequence,
    is TriplePattern.SimpleAlts,
    is TriplePattern.UnboundSequence,
    is TriplePattern.Negated,
    is TriplePattern.OneOrMore,
    is TriplePattern.ZeroOrMore -> null
    is TriplePattern.NamedBinding -> this
}
