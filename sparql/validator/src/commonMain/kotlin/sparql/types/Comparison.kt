package sparql.types

import dev.tesserakt.sparql.Bindings
import dev.tesserakt.sparql.toBindings
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
    val stable1 = a.map { it.sortedBy { it.first } }
    val stable2 = b.map { it.sortedBy { it.first } }
    val counts = stable1.groupingBy { it }.eachCount().toMutableMap()
    stable2.forEach {
        counts.replace(it) { v -> (v ?: 0) - 1 }
    }
    if (counts.all { it.value == 0 }) {
        return ExactMatch
    }
    return ComparisonResult(
        missing = counts.filter { it.value > 0 }.flatMap { entry -> List(entry.value) { entry.key.toBindings() } },
        leftOver = counts.filter { it.value < 0 }.flatMap { entry -> List(-entry.value) { entry.key.toBindings() } },
    )
}
