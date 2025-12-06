package dev.tesserakt.sparql.util

import dev.tesserakt.util.replace

class Counter<T : Any> private constructor(private val map: MutableMap<T, Int>): Iterable<Map.Entry<T, Int>> {

    constructor(): this(mutableMapOf())

    constructor(elements: Iterable<T>): this(map = elements.groupingBy { it }.eachCountTo(mutableMapOf()))

    // various state-related properties; they're private as they make more sense when used in their respective
    //  collections, i.e. `current.size` and `flattened.size`
    /**
     * The total number of inserted items (= sum of all node counts)
     */
    private var count = 0

    val current: Set<T> get() = map.keys

    operator fun get(value: T) = map[value] ?: 0

    operator fun contains(value: T) = get(value) > 0

    fun increment(value: T, count: Int = 1) {
        map.replace(value) { original -> (original ?: 0) + count }
        // just in case `map.replace` would fail, we only update our own state afterward
        this.count += count
    }

    fun increment(changes: Map<T, Int>) {
        changes.forEach { (value, count) -> increment(value, count) }
    }

    fun decrement(value: T, count: Int = 1) {
        map.replace(value) { current ->
            when {
                current == null || current < count -> {
                    // we're not actually mutating anything here, so we're not updating the count state either
                    throw NoSuchElementException()
                }
                current == count -> {
                    this.count -= count
                    null
                }
                else -> {
                    this.count -= count
                    current - count
                }
            }
        }
    }

    fun decrement(changes: Map<T, Int>) {
        changes.forEach { (value, count) -> decrement(value, count) }
    }

    fun clear(value: T) {
        count = 0
        map.remove(value)
    }

    fun clone() = Counter(map.toMutableMap())

    val flattened = object: Collection<T> {

        override val size: Int
            get() = this@Counter.count

        override fun iterator(): Iterator<T> = object: Iterator<T> {
            private val iter = this@Counter.iterator()
            private var node = if (iter.hasNext()) iter.next() else null
            private var remaining = node?.value ?: 0

            override fun hasNext(): Boolean {
                return remaining > 0
            }

            override fun next(): T {
                val cur = node
                if (remaining <= 0 || cur == null) {
                    throw NoSuchElementException()
                }
                if (--remaining <= 0) {
                    node = if (iter.hasNext()) {
                        val next = iter.next()
                        remaining = next.value
                        next
                    } else {
                        null
                    }
                }
                return cur.key
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
