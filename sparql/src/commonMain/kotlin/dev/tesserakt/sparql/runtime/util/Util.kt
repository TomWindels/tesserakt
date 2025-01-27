package dev.tesserakt.sparql.runtime.util

import dev.tesserakt.sparql.runtime.common.types.Pattern
import dev.tesserakt.sparql.runtime.incremental.types.*

fun Query.QueryBody.getAllNamedBindings(): Set<Pattern.RegularBinding> =
    buildSet {
        addAll(patterns.getAllNamedBindings())
        optional.forEach { optional -> addAll(optional.patterns.getAllNamedBindings()) }
        unions.forEach { union -> union.forEach { segment -> addAll(segment.getAllNamedBindings()) } }
    }

fun Query.QueryBody.getAllBindings(): Set<Pattern.Binding> =
    buildSet {
        addAll(patterns.getAllBindings())
        optional.forEach { optional -> addAll(optional.patterns.getAllBindings()) }
        unions.forEach { union -> union.forEach { segment -> addAll(segment.getAllBindings()) } }
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

/**
 * Extracts all bindings from the original query
 */
fun Patterns.getAllBindings(): Set<Pattern.Binding> =
    buildSet {
        this@getAllBindings.forEach { pattern ->
            addAll(pattern.getAllBindings())
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

fun Pattern.getAllBindings(): Set<Pattern.Binding> {
    return if (
        s !is Pattern.Binding &&
        p !is Pattern.Binding &&
        o !is Pattern.Binding
    ) {
        emptySet()
    } else buildSet {
        (s as? Pattern.Binding)?.let { add(it) }
        p.getBinding()?.let { add(it) }
        (o as? Pattern.Binding)?.let { add(it) }
    }
}

fun Segment.getAllNamedBindings(): Set<Pattern.RegularBinding> {
    return when (this) {
        is SelectQuerySegment ->
            query.output.map { output -> Pattern.RegularBinding(output.name) }.toSet()

        is StatementsSegment ->
            statements.getAllNamedBindings()
    }
}

fun Segment.getAllBindings(): Set<Pattern.Binding> {
    return when (this) {
        is SelectQuerySegment ->
            query.output.map { output -> Pattern.RegularBinding(output.name) }.toSet()

        is StatementsSegment ->
            statements.getAllBindings()
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


private fun Pattern.Predicate.getBinding(): Pattern.Binding? = when (this) {
    is Pattern.Alts,
    is Pattern.Exact,
    is Pattern.Sequence,
    is Pattern.SimpleAlts,
    is Pattern.UnboundSequence,
    is Pattern.Negated,
    is Pattern.OneOrMore,
    is Pattern.ZeroOrMore -> null
    is Pattern.Binding -> this
}
