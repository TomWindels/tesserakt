package dev.tesserakt.sparql.runtime.util

class Bitmask private constructor(
    private val bits: Int,
    val length: Int
): Iterable<Int> {

    operator fun get(index: Int) = ((bits shr index) and 1) == 1

    infix fun or(other: Bitmask): Bitmask = Bitmask(
        bits = this.bits or other.bits,
        length = maxOf(this.length, other.length)
    )

    infix fun and(other: Bitmask): Bitmask = Bitmask(
        bits = this.bits and other.bits,
        length = minOf(this.length, other.length)
    )

    operator fun contains(index: Int): Boolean = index <= length && this.bits and (1 shl index) != 0

    // if this bitmask is equivalent to all 1's (completely true)
    fun isOne(): Boolean {
        val ones = ones(length)
        return (this.bits and ones) == ones
    }

    // if this bitmask is equivalent to all 0's (completely false)
    fun isZero(): Boolean {
        return (this.bits and ones(length)) == 0
    }

    /**
     * Inverses the bits found in `this` bitmask, e.g. `0x0011` -> `0x1100`
     */
    fun inv() = Bitmask(bits = bits.inv() and ones(length), length = length)

    companion object {

        fun from(booleans: Collection<Boolean>): Bitmask {
            var result = 0
            var offset = 0
            val iterator = booleans.iterator()
            while (iterator.hasNext()) {
                if (iterator.next()) {
                    result = result or (1 shl offset)
                }
                ++offset
            }
            return Bitmask(bits = result, length = booleans.size)
        }

        fun wrap(raw: Int, length: Int): Bitmask = Bitmask(bits = raw, length = length)

        fun onesAt(index: Int, length: Int? = null): Bitmask {
            val result = 1 shl index
            return Bitmask(bits = result, length = length ?: index)
        }

        fun onesAt(index: Int, vararg other: Int, length: Int? = null): Bitmask {
            val result = other.fold(initial = 1 shl index) { current, i -> current or (1 shl i) }
            return Bitmask(bits = result, length = length ?: maxOf(index, *other))
        }

        // returns 1's `length` long, preceded by 0's
        private fun ones(length: Int) = (UInt.MAX_VALUE shl length).inv().toInt()

    }

    override fun iterator(): Iterator<Int> {
        return iterator {
            var remaining = bits
            while (remaining != 0) {
                val bit = remaining.takeLowestOneBit()
                val index = bit.countTrailingZeroBits()
                if (index >= length) {
                    break
                }
                remaining = remaining xor bit
                yield(index)
            }
        }
    }

    /**
     * Returns a copy of this bitmask, but with the bit at [index] set.
     */
    fun withOnesAt(index: Int): Bitmask {
        return Bitmask(bits = bits or (1 shl index), length = length)
    }

    /**
     * Returns a copy of this bitmask, but with the bits at [index] set.
     */
    fun withOnesAt(vararg index: Int): Bitmask {
        return Bitmask(bits = index.fold(bits) { result, i -> result or (1 shl i) }, length = length)
    }

    /**
     * Counts the number of ones inside this bitmask.
     *
     * Example: `0b0110` returns `2`
     */
    fun count() = bits.countOneBits()

    /**
     * Returns the length of this bitmask.
     *
     * Example: `0b0110` returns `4`
     */
    fun size() = length

    /**
     * Returns the index of the lowest non-zero bit.
     *
     * Example: `0b0010` returns 1
     */
    fun lowestOneBitIndex() = bits.countTrailingZeroBits()

    /**
     * Returns the index of the lowest zero bit.
     *
     * Example: `0b0011` returns 2
     */
    fun lowestZeroBitIndex() = bits.inv().countTrailingZeroBits()

    /**
     * Returns the index of the highest non-zero bit.
     *
     * Example: `0b1010` returns 3
     */
    fun highestOneBitIndex() = Int.SIZE_BITS - bits.countLeadingZeroBits() - 1

    /**
     * Returns the index of the highest zero bit.
     *
     * Example: `0b0010` returns 3
     */
    fun highestZeroBitIndex() = Int.SIZE_BITS - bits.inv().countLeadingZeroBits()

    override fun toString(): String {
        return "0b${bits.toBinaryString(length)}"
    }

}

private fun Int.toBinaryString(length: Int = 32): String = buildString(length) {
    repeat(length) { i ->
        val c = if (this@toBinaryString and (1 shl (length - i - 1)) != 0) '1' else '0'
        append(c)
    }
}
