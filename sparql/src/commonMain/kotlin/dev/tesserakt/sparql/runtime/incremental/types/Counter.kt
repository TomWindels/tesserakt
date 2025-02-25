package dev.tesserakt.sparql.runtime.incremental.types

import kotlin.jvm.JvmInline

@JvmInline
value class Counter<T> private constructor(private val map: MutableMap<T, Int>): Iterable<Map.Entry<T, Int>> {

    constructor(): this(mutableMapOf())

    constructor(elements: Iterable<T>): this(map = elements.groupingBy { it }.eachCountTo(mutableMapOf()))

    val current: Set<T> get() = map.keys

    operator fun get(value: T) = map[value] ?: 0

    operator fun contains(value: T) = get(value) > 0

    fun increment(value: T, count: Int = 1) {
        map[value] = this[value] + count
    }

    fun increment(changes: Map<T, Int>) {
        changes.forEach { (value, count) -> increment(value, count) }
    }

    fun decrement(value: T, count: Int = 1) {
        val current = this[value]
        when {
            current <= count -> { map.remove(value) }
            else -> { map[value] = current - count }
        }
    }

    fun decrement(changes: Map<T, Int>) {
        changes.forEach { (value, count) -> decrement(value, count) }
    }

    fun clear(value: T) {
        map.remove(value)
    }

    fun clone() = Counter(map.toMutableMap())

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

}
