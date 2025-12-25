package dev.tesserakt.sparql.runtime.collection


/**
 * The size of a single [IntArray] slot used in the [GrowingIntArray] implementation. The size value is derived from
 *  the following characteristics:
 *  * a multiple of 2: allows the modulo operator to be replaced by bitwise operators
 *  * a multiple of 16 (ints) (= 64 bytes): typical CPU cache line size
 *  * less than 128k (ints) (=512 KiB), as this is half of the smallest possible heap size region size for G1 GC,
 *    which would make it eligible to become a 'humongous object'
 */
private const val GROWING_INT_SLOT_CAPACITY = 1 shl 7 // 128

private const val GROWING_INT_SLOT_BIT = 7

/**
 * A collection of integers, backed by a growing set of [IntArray]s, offering improved performance during iteration.
 * IMPORTANT: this collection's [removeRange] does NOT preserve element order!
 */
class GrowingIntArray: Iterable<Int> {

    val size: Int get() = (slots.size shl GROWING_INT_SLOT_BIT) - finalSlotRemaining

    private val slots = mutableListOf<IntArray>()

    /**
     * The remaining number of int's in the final slot. This is guaranteed to be less than [GROWING_INT_SLOT_CAPACITY], as empty
     *  slots are not kept in the [slots] collection.
     */
    private var finalSlotRemaining = 0

    override fun iterator(): IntIterator = object: IntIterator() {
        private var i = 0

        override fun hasNext(): Boolean {
            return i < size
        }

        override fun nextInt(): Int {
            if (i >= size) {
                throw NoSuchElementException()
            }
            return this@GrowingIntArray[i++]
        }

    }

    fun add(value: Int) {
        if (finalSlotRemaining == 0) {
            val new = IntArray(GROWING_INT_SLOT_CAPACITY)
            new[0] = value
            slots.add(new)
            finalSlotRemaining = GROWING_INT_SLOT_CAPACITY - 1
        } else {
            slots.last()[GROWING_INT_SLOT_CAPACITY - finalSlotRemaining] = value
            --finalSlotRemaining
        }
    }

    /**
     * Removes the element at [index] and [count] subsequent elements by overwriting it with the last elements in
     *  this [GrowingIntArray]
     */
    fun removeRange(index: Int, count: Int = 1) {
        // we're not decreasing the count ourselves - `pop` does that
        if (index + count == size) {
            // simply popping `count` times; the data retrieved from it is what's being removed
            repeat(count) { pop() }
            return
        }
        // finding the appropriate slot to remove from
        var i = index + count
        repeat(count) {
            --i
            this[i] = pop()
        }
    }

    /**
     * Pops the last element, reducing this collection's size by one, and returning that element
     */
    private inline fun pop(): Int {
        val i = GROWING_INT_SLOT_CAPACITY - finalSlotRemaining - 1
        val result = slots.last()[i]
        if (i == 0) {
            // freeing our buffer, it's now empty
            slots.removeAt(slots.size - 1)
            finalSlotRemaining = 0
        } else {
            // marking it as freed, but not actually replacing any memory
            ++finalSlotRemaining
        }
        return result
    }

    operator fun get(index: Int): Int {
        return slots[index shr GROWING_INT_SLOT_BIT][index and (GROWING_INT_SLOT_CAPACITY - 1)]
    }

    operator fun set(index: Int, value: Int) {
        slots[index shr GROWING_INT_SLOT_BIT][index and (GROWING_INT_SLOT_CAPACITY - 1)] = value
    }

    override fun toString() = "GrowingIntArray[$size element(s), ${slots.size} slot(s), $finalSlotRemaining remaining capacity]"
}
