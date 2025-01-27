package dev.tesserakt.sparql.runtime.util

import dev.tesserakt.sparql.runtime.common.types.Pattern
import dev.tesserakt.sparql.runtime.incremental.types.*

fun Query.QueryBody.getAllNamedBindings(): Set<Pattern.RegularBinding> =
    buildSet {
        addAll(patterns.getAllNamedBindings())
        optional.forEach { optional -> addAll(optional.segment.getAllNamedBindings()) }
        unions.forEach { union -> union.forEach { segment -> addAll(segment.getAllNamedBindings()) } }
    }

/**
 * Extracts all named bindings from the original query (excluding generated ones from the AST)
 */
fun Patterns.getAllNamedBindings(): Set<Pattern.RegularBinding> =
    buildSet {
        this@getAllNamedBindings.forEach { pattern ->
            addAll(pattern.getAllNamedBindings())
        }
    }

fun Pattern.getAllNamedBindings(): Set<Pattern.RegularBinding> {
    return if (
        s !is Pattern.RegularBinding &&
        p !is Pattern.RegularBinding &&
        o !is Pattern.RegularBinding
    ) {
        emptySet()
    } else buildSet {
        (s as? Pattern.RegularBinding)?.let { add(it) }
        p.getNamedBinding()?.let { add(it) }
        (o as? Pattern.RegularBinding)?.let { add(it) }
    }
}

private fun Segment.getAllNamedBindings(): Set<Pattern.RegularBinding> {
    return when (this) {
        is SelectQuerySegment ->
            query.output.map { output -> Pattern.RegularBinding(output.name) }.toSet()

        is StatementsSegment ->
            statements.getAllNamedBindings()
    }
}

private fun Pattern.Predicate.getNamedBinding(): Pattern.RegularBinding? = when (this) {
    is Pattern.Alts,
    is Pattern.Exact,
    is Pattern.GeneratedBinding,
    is Pattern.Sequence,
    is Pattern.SimpleAlts,
    is Pattern.UnboundSequence,
    is Pattern.Negated,
    is Pattern.OneOrMore,
    is Pattern.ZeroOrMore -> null
    is Pattern.RegularBinding -> this
}
