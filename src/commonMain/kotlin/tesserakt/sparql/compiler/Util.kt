package tesserakt.sparql.compiler

import tesserakt.sparql.compiler.types.Pattern

fun Pattern.bindings(): List<Pattern.Binding> {
    val result = mutableListOf<Pattern.Binding>()
    if (s is Pattern.Binding) {
        result.add(s)
    }
    result.addAll(p.bindings())
    if (o is Pattern.Binding) {
        result.add(o)
    }
    return when (result.size) {
        0 -> emptyList()
        else -> result
    }
}

// helper for the helper

private fun Pattern.Predicate.bindings(): List<Pattern.Binding> {
    return when (this) {
        is Pattern.Chain -> list.flatMap { it.bindings() }
        is Pattern.Constrained -> allowed.flatMap { it.bindings() }
        is Pattern.Binding -> listOf(this)
        is Pattern.Exact -> emptyList()
        is Pattern.Not -> predicate.bindings()
        is Pattern.Repeating -> value.bindings()
    }
}
