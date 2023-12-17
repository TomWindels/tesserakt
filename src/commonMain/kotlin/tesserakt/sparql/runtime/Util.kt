package tesserakt.sparql.runtime

import tesserakt.sparql.runtime.types.PatternASTr
import tesserakt.sparql.runtime.types.PatternsASTr
import tesserakt.sparql.runtime.types.QueryASTr

fun QueryASTr.QueryBodyASTr.getAllNamedBindings(): Set<PatternASTr.RegularBinding> =
    buildSet {
        addAll(patterns.getAllNamedBindings())
        optional.forEach { pattern -> addAll(pattern.getAllNamedBindings()) }
        unions.forEach { union -> union.forEach { pattern -> addAll(pattern.getAllNamedBindings()) } }
    }

/**
 * Extracts all named bindings from the original query (excluding generated ones from the AST)
 */
fun PatternsASTr.getAllNamedBindings(): Set<PatternASTr.RegularBinding> =
    buildSet {
        this@getAllNamedBindings.forEach { pattern ->
            addAll(pattern.getAllNamedBindings())
        }
    }

fun PatternASTr.getAllNamedBindings(): Set<PatternASTr.RegularBinding> {
    return if (
        s !is PatternASTr.RegularBinding &&
        p !is PatternASTr.RegularBinding &&
        o !is PatternASTr.RegularBinding
    ) {
        emptySet()
    } else buildSet {
        (s as? PatternASTr.RegularBinding)?.let { add(it) }
        p.getNamedBinding()?.let { add(it) }
        (o as? PatternASTr.RegularBinding)?.let { add(it) }
    }
}

private fun PatternASTr.Predicate.getNamedBinding(): PatternASTr.RegularBinding? = when(this) {
    is PatternASTr.Alts,
    is PatternASTr.Exact,
    is PatternASTr.Inverse,
    is PatternASTr.GeneratedBinding,
    is PatternASTr.OneOrMoreFixed,
    is PatternASTr.ZeroOrMoreFixed -> null
    is PatternASTr.RegularBinding -> this
    is PatternASTr.OneOrMoreBound -> predicate
    is PatternASTr.ZeroOrMoreBound -> predicate
}
