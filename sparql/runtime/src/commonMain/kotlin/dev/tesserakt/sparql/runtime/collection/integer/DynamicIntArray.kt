package dev.tesserakt.sparql.runtime.collection.integer

import kotlin.math.min

/**
 * Bit representing the size of a single [IntArray] instance
 */
private const val UNIT_SIZE_BIT = 10

/**
 * Size of a single [IntArray] instance
 */
private const val UNIT_SIZE = 2 shl UNIT_SIZE_BIT

/**
 * Similar to [ArrayList] of type [Int], but backed by [IntArray] to avoid boxing, implementing as many boxing-free
 *  methods as possible.
 *
 * Not a [MutableCollection] instance as that would prevent [iterator] from being an [IntIterator] implementation.
 */
class DynamicIntArray: Collection<Int> {

    private val backing = ArrayList<IntArray>()

    val capacity: Int
        get() = backing.size shl UNIT_SIZE_BIT

    val free: Int
        get() = capacity - size

    override var size: Int = 0
        private set

    override fun isEmpty(): Boolean {
        return size == 0
    }

    fun add(element: Int): Boolean {
        preallocate(1)
        backing.last()[size and (UNIT_SIZE - 1)] = element
        ++size
        return true
    }

    fun addAll(elements: Collection<Int>): Boolean {
        // whilst it is possible `elements` are also a `DynamicIntArray`, we can't take over references
        //  to its elements as we otherwise would mutate each other
        preallocate(elements.size)
        elements.forEach { element ->
            backing[size shr UNIT_SIZE_BIT][size and (UNIT_SIZE - 1)] = element
            ++size
        }
        return true
    }

    fun pop(count: Int = 1) {
        if (size < count) {
            throw NoSuchElementException("Tried to remove $count element(s) whilst only $size element(s) are available!")
        }
        // we don't need to actually clear the underlying data as it has no impact on the amount of bytes owned by the
        //  array; only in corner cases where a complete block could be freed would have impact on the amount of memory
        //  being used
        size -= count
    }

    /**
     * Ensures there are at least [extraCapacity] [free] space
     */
    private fun preallocate(extraCapacity: Int) {
        while (free < extraCapacity) {
            backing.add(IntArray(UNIT_SIZE))
        }
    }

    fun clear() {
        backing.clear()
        size = 0
    }

    operator fun get(index: Int): Int {
        if (index < 0 || index >= size) {
            throw IndexOutOfBoundsException("Index $index is not inside range 0 .. $size")
        }
        return backing[index shr UNIT_SIZE_BIT][index and (UNIT_SIZE - 1)]
    }

    operator fun set(index: Int, value: Int) {
        if (index < 0 || index >= size) {
            throw IndexOutOfBoundsException("Index $index is not inside range 0 .. $size")
        }
        backing[index shr UNIT_SIZE_BIT][index and (UNIT_SIZE - 1)] = value
    }

    override fun iterator(): IntIterator {
        return object: IntIterator() {

            private var i = 0

            override fun nextInt(): Int {
                if (i >= size) {
                    throw NoSuchElementException()
                }
                val value = backing[i shr UNIT_SIZE_BIT][i and (UNIT_SIZE - 1)]
                ++i
                return value
            }

            override fun hasNext(): Boolean {
                return i < size
            }

        }
    }

    override fun contains(element: Int): Boolean {
        return any { element == it }
    }

    override fun containsAll(elements: Collection<Int>): Boolean {
        return elements.all { it in this }
    }

    override fun toString(): String {
        return if (isEmpty()) {
            "DynamicIntArray (empty)"
        } else buildString {
            append("DynamicIntArray [")
            append(this@DynamicIntArray[0])
            repeat(min(size, 10) - 1) { i ->
                append(", ")
                append(this@DynamicIntArray[i + 1])
            }
            if (size > 10) {
                append(" ...] (")
                append(size)
                append(" elements)")
            } else {
                append(']')
            }
        }
    }

}
