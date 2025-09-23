package dev.tesserakt.sparql.util

import dev.tesserakt.util.replace
import kotlin.jvm.JvmInline

@JvmInline
value class Counter<T : Any> private constructor(private val map: MutableMap<T, Int>): Iterable<Map.Entry<T, Int>> {

    constructor(): this(mutableMapOf())

    constructor(elements: Iterable<T>): this(map = elements.groupingBy { it }.eachCountTo(mutableMapOf()))

    val current: Set<T> get() = map.keys

    operator fun get(value: T) = map[value] ?: 0

    operator fun contains(value: T) = get(value) > 0

    fun increment(value: T, count: Int = 1) {
        map.replace(value) { original -> (original ?: 0) + count }
    }

    fun increment(changes: Map<T, Int>) {
        changes.forEach { (value, count) -> increment(value, count) }
    }

    fun decrement(value: T, count: Int = 1) {
        map.replace(value) { current ->
            when {
                current == null || current < count -> throw NoSuchElementException()
                current == count -> { null }
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

    fun clone() = Counter(map.toMutableMap())

    fun flatten() = object: Collection<T> {

        override val size: Int by lazy { this@Counter.map.asIterable().sumOf { it.value } }

        override fun iterator(): Iterator<T> = object: Iterator<T> {

            private val iter = this@Counter.iterator()
            private lateinit var current: T
            private var remaining = 0

            override fun hasNext(): Boolean {
                ensureNext()
                return remaining > 0
            }

            override fun next(): T {
                ensureNext()
                if (remaining == 0) {
                    throw NoSuchElementException()
                }
                --remaining
                return current
            }

            private fun ensureNext() {
                if (remaining > 0) {
                    return
                }
                while (iter.hasNext() && remaining == 0) {
                    val (next, count) = iter.next()
                    current = next
                    remaining = count
                }
            }

        }

        override fun isEmpty(): Boolean {
            return this@Counter.map.asIterable().none { it.value > 0 }
        }

        override fun contains(element: T): Boolean {
            return this@Counter[element] > 0
        }

        override fun containsAll(elements: Collection<T>): Boolean {
            throw UnsupportedOperationException()
        }

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

}
