package sparql.types

import dev.tesserakt.sparql.types.runtime.evaluation.Bindings
import dev.tesserakt.util.replace


data class ComparisonResult(
    val missing: List<Bindings>,
    val leftOver: List<Bindings>
)

val ExactMatch = ComparisonResult(emptyList(), emptyList())

fun fastCompare(
    a: List<Bindings>,
    b: List<Bindings>
): ComparisonResult {
    val counts = a.groupingBy { it }.eachCount().toMutableMap()
    b.forEach {
        counts.replace(it) { v -> (v ?: 0) - 1 }
    }
    if (counts.all { it.value == 0 }) {
        return ExactMatch
    }
    return ComparisonResult(
        missing = counts.filter { it.value > 0 }.flatMap { entry -> List(entry.value) { entry.key } },
        leftOver = counts.filter { it.value < 0 }.flatMap { entry -> List(-entry.value) { entry.key } },
    )
}
