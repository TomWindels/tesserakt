package dev.tesserakt.benchmarking

import dev.tesserakt.util.replace


/**
 * Compares results from the first set [a] with the second set [b], where [a] represents the most up-to-date version
 *  of the result that is being compared
 */
fun compare(a: List<Any>, b: List<Any>, checksum: Int): Evaluator.Output {
    val counts = a.groupingBy { it }.eachCount().toMutableMap()
    b.forEach {
        counts.replace(it) { v -> (v ?: 0) - 1 }
    }
    return Evaluator.Output(
        added = counts.filter { it.value > 0 }.entries.sumOf { entry -> entry.value },
        removed = counts.filter { it.value < 0 }.entries.sumOf { entry -> -entry.value },
        checksum = checksum,
    )
}
