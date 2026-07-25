package dev.tesserakt.sparql.runtime.collection.integer

fun IntArray.createView(start: Int = 0, size: Int = this.size - start) : IntArrayView = IntArrayView(
    backing = this,
    offset = start,
    len = size,
)

fun viewOf(vararg element: Int) : IntArrayView {
    val backing = intArrayOf(*element)
    return backing.createView()
}

fun IntCollectionView.toIntArray(): IntArray = IntArray(size) { this@toIntArray[it] }
