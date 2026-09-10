package dev.tesserakt.sparql.types


/* helpers for using the pattern element types */

val TriplePattern.Subject.bindingName: String?
    get() = (this as? TriplePattern.Binding)?.name

val TriplePattern.Object.bindingName: String?
    get() = (this as? TriplePattern.Binding)?.name

val TriplePattern.Predicate.bindingName: String?
    get() = (this as? TriplePattern.Binding)?.name

fun GraphPattern.extractAllBindings(): Set<TriplePattern.Binding> =
    statements.flatMapTo(mutableSetOf()) { statement ->
        when (statement) {
            is Optional -> statement.patterns.flatMapTo(mutableSetOf()) { it.extractAllBindings() }
            is TriplePattern -> statement.extractAllBindings()
            is Union -> statement.flatMapTo(mutableSetOf()) { it.extractAllBindings() }
        }
    }

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
