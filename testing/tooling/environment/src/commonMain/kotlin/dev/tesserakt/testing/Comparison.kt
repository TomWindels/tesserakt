package dev.tesserakt.testing

import dev.tesserakt.util.replace


data class Comparison<T>(
    val missing: List<T>,
    val leftOver: List<T>,
) {

    fun isIdentical(): Boolean = missing.isEmpty() && leftOver.isEmpty()

    inline fun <R> map(transform: (T) -> R): Comparison<R> =
        Comparison(missing = missing.map(transform), leftOver = leftOver.map(transform))

    override fun toString(): String {
        return when {
            missing.isEmpty() && leftOver.isEmpty() -> "Exact match!"

            leftOver.isEmpty() -> {
                "There are ${missing.size} items missing!\n${missing.joinToString()}"
            }

            missing.isEmpty() -> {
                "There are ${leftOver.size} items left over!\n${leftOver.joinToString()}"
            }

            else -> {
                "There are items missing and items left over!\n* ${missing.size} missing:\n${missing.joinToString()}\n* ${leftOver.size} left over:\n${leftOver.joinToString()}"
            }
        }
    }

}

val ExactMatch = Comparison<Nothing>(emptyList(), emptyList())

fun <T> comparisonOf(
    a: Iterable<T>,
    b: Iterable<T>
): Comparison<T> {
    val counts = a.groupingBy { it }.eachCount().toMutableMap()
    b.forEach {
        counts.replace(it) { v -> (v ?: 0) - 1 }
    }
    if (counts.all { it.value == 0 }) {
        @Suppress("UNCHECKED_CAST")
        return ExactMatch as Comparison<T>
    }
    return Comparison(
        missing = counts.filter { it.value > 0 }.flatMap { entry -> List(entry.value) { entry.key } },
        leftOver = counts.filter { it.value < 0 }.flatMap { entry -> List(-entry.value) { entry.key } },
    )
}
