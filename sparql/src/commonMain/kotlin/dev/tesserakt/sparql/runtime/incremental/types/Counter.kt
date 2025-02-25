package dev.tesserakt.sparql.runtime.incremental.types

import dev.tesserakt.util.replace
import kotlin.jvm.JvmInline
import kotlin.jvm.JvmName

@JvmInline
value class Counter<T> private constructor(private val map: MutableMap<T, Int>): Iterable<Map.Entry<T, Int>> {

    constructor(): this(mutableMapOf())

    val current: Set<T> get() = map.keys

    operator fun get(value: T) = map[value] ?: 0

    operator fun contains(value: T) = value in map

    fun increment(value: T, count: Int = 1) {
        map.replace(value) { current -> (current ?: 0) + count }
    }

    fun increment(changes: Map<T, Int>) {
        changes.forEach { (value, count) -> increment(value, count) }
    }

    fun decrement(value: T, count: Int = 1) {
        map.replace(value) { current ->
            when {
                current == null || current < count -> { throw IllegalStateException("Could not decrement $value ($current < $count)") }
                current == count -> { 0 }
                else -> { current - count }
            }
        }
    }

    fun decrement(changes: Map<T, Int>) {
        changes.forEach { (value, count) -> decrement(value, count) }
    }

    fun clear(value: T) {
        map.remove(value)
    }

    fun clear(predicate: (T) -> Boolean) {
        map.entries.retainAll { !predicate(it.key) }
    }

    override fun iterator(): Iterator<Map.Entry<T, Int>> {
        return map.iterator()
    }

    override fun toString(): String {
        return joinToString(
            separator = ", ",
            prefix = "Counter { ",
            postfix = " }",
            transform = { (key, count) -> "$key ($count)" }
        )
    }

    companion object {

        @JvmName("flattenEntries")
        fun <T> Iterable<Map.Entry<T, Int>>.flatten(): List<T> = buildList(sumOf { it.value }) {
            this@flatten.forEach { (item, count) -> repeat(count) { add(item) } }
        }

        @JvmName("flattenPairs")
        fun <T> Iterable<Pair<T, Int>>.flatten(): List<T> = buildList(sumOf { it.second }) {
            this@flatten.forEach { (item, count) -> repeat(count) { add(item) } }
        }

    }

}
