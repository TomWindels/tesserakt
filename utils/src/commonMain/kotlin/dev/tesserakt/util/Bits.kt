package dev.tesserakt.util


fun Int.bitIterator() = BitIterator(this)

class BitIterator(private var bits: Int): IntIterator() {
    override fun hasNext(): Boolean {
        return bits != 0
    }

    override fun nextInt(): Int {
        val bit = bits.takeLowestOneBit()
        val index = bit.countTrailingZeroBits()
        bits = bits xor bit
        return index
    }

    fun remaining(): Int {
        return bits.countOneBits()
    }

}
