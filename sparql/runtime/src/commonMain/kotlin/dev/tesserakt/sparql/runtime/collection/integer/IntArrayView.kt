package dev.tesserakt.sparql.runtime.collection.integer

class IntArrayView(
    private val backing: IntArray,
    var offset: Int,
    var len: Int,
) : IntCollectionView() {

    override val size: Int
        get() = len

    override operator fun get(index: Int): Int {
        // the range variant is not desired here
//        @Suppress("ConvertTwoComparisonsToRangeCheck")
//        if (index < 0 || index >= len || offset + index >= backing.size) {
//            throw NoSuchElementException("Index $index is out of range!")
//        }
        return backing[index + offset]
    }

}
