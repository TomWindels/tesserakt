package dev.tesserakt.sparql.runtime.collection.integer

import dev.tesserakt.sparql.runtime.collection.GrowingIntArray

class GrowingIntArrayView(
    private val backing: GrowingIntArray,
    private var offset: Int,
    private var len: Int,
) : IntCollectionView() {

    override val size: Int
        get() = len

    fun setView(start: Int, size: Int) {
        require(start >= 0 && start + len < backing.size)
        offset = start
        len = size
    }

    override operator fun get(index: Int): Int {
        // the range variant is not desired here
        @Suppress("ConvertTwoComparisonsToRangeCheck")
        if (index < 0 || index >= len || offset + index >= backing.size) {
            throw NoSuchElementException("Index $index is out of range!")
        }
        return backing[index + offset]
    }

    override fun iterator() = object: IntIterator() {
        private var i = 0
        override fun hasNext(): Boolean {
            return i < len
        }

        override fun nextInt(): Int {
            if (i >= len) {
                throw NoSuchElementException()
            }
            return backing[i++ + offset]
        }
    }

}
