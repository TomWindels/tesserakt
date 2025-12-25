package dev.tesserakt.sparql.runtime.collection.integer

import dev.tesserakt.sparql.runtime.collection.integer.IntHashMap.Companion.NOT_FOUND


// TODO: redo the data structure, using an arraylist of fixed size int arrays, these being the various buckets
//  the size of a bucket is large enough to fit X key-value pairs, X being the max number of hash collisions before
//  triggering a doubling in size + rehash
//  finding an element finds the correct bucket, goes through all keys until it finds a matching key value or until a
//   sentinel value is reached (no hash value check required anymore, given we're checking a single bucket)
//  insertions calculates the appropriate bucket to use, appending the key-value pair to that bucket, possibly
//   triggering a resize first
//  removing an element simply removes it from the bucket by overwriting it with the last element for that bucket,
//   and marks that last element as 'removed' using a sentinel value
class IntHashMap(
    /**
     * The number of [Int] elements taken up by a single key value, must be larger than 0
     */
    private val n: Int,
    /**
     * The amount of elements this map should be able to contain
     */
    capacity: Int,
) : Iterable<Pair<IntCollectionView, Int>> {

    companion object {
        private const val SENTINEL_VALUE = Int.MIN_VALUE
        const val NOT_FOUND = Int.MIN_VALUE
    }

    private val buckets = ArrayList<IntArray>((capacity - 1).takeHighestOneBit())

    init {
        require(n > 0)
        val initialBucketCount = (capacity - 1).takeHighestOneBit()
        repeat(initialBucketCount) {
            buckets.add(IntArray((n + 1) * 5) { SENTINEL_VALUE })
        }
    }

    /**
     * Sets the provided [value] for the given [key]. Returns `false` if unsuccessful (which happens once
     *  the [key] value is not currently present in the map, and the number of hash collisions would cause
     *  a key-value pair to spill outside the internal buffer)
     */
    operator fun set(key: IntCollectionView, value: Int): Boolean {
        return findBucket(key).set(key, value)
    }

    /**
     * Gets the value associated with the given [key], or special value [NOT_FOUND] if not found
     */
    operator fun get(key: IntCollectionView): Int {
        return findBucket(key).findValue(key)
    }

    /**
     * Removes the key-value pair associated with the given [key], returning its currently associated value, or
     *  [NOT_FOUND] if the [key] is not currently present inside the map
     */
    fun remove(key: IntCollectionView): Int {
        return findBucket(key).remove(key)
    }

    override fun iterator(): Iterator<Pair<IntCollectionView, Int>> = object: Iterator<Pair<IntCollectionView, Int>> {

        private var bucketIndex = run {
            val index = buckets.indexOfFirst { it[0] != SENTINEL_VALUE }
            if (index == -1) {
                buckets.size
            } else {
                index
            }
        }
        private var elementIndex = 0

        override fun hasNext(): Boolean {
            return bucketIndex < buckets.size
        }

        override fun next(): Pair<IntCollectionView, Int> {
            val bucket = buckets[bucketIndex]
            val key = bucket.createView(elementIndex, n)
            val value = bucket[elementIndex + n]
            elementIndex += n + 1
            // going to the next bucket if we've exhausted or current bucket index
            if (elementIndex >= bucket.size || bucket[elementIndex] == SENTINEL_VALUE) {
                elementIndex = 0
                ++bucketIndex
                while (bucketIndex < buckets.size && buckets[bucketIndex][0] == SENTINEL_VALUE) {
                    ++bucketIndex
                }
            }
            return key to value
        }

    }

    /* helpers */

    /**
     * Returns the bucket [IntArray] from the list of [buckets] associated with the given [key].
     */
    private inline fun findBucket(key: IntCollectionView): IntArray {
        return buckets[hash(key)]
    }

    /**
     * Returns the index associated with the given [key] inside [this] bucket, or `-1` in case this bucket
     *  does not contain the given element.
     */
    private inline fun IntArray.findKey(key: IntCollectionView): Int {
        // simply scanning through the list until we reach a matching key, or a sentinel value
        val view = createView(size = n)
        // buckets are never 0-sized, so the first check can be ignored
        do {
            // we can bail early if we reached the last element
            if (view[0] == SENTINEL_VALUE) {
                return -1
            }
            if (view == key) {
                return view.offset
            }
            // jumping K-V number of ints
            view.offset += n + 1
        } while (view.offset < size)
        return -1
    }

    /**
     * Finds the value in [this] bucket associated with the given [key], or `NOT_FOUND`.
     */
    private inline fun IntArray.findValue(key: IntCollectionView): Int {
        val pos = findKey(key)
        return if (pos == -1) {
            NOT_FOUND
        } else {
            this[pos + n]
        }
    }

    /**
     * Appends the given [key]-[value] pair to [this] bucket, returning `true` upon success, or `false` if the bucket
     *  is already filled.
     */
    private inline fun IntArray.set(key: IntCollectionView, value: Int): Boolean {
        val view = createView(start = 0, size = n)
        while (view.offset < size) {
            if (view == key) {
                // we can replace the value directly, the key already matches so that doesn't have to be overwritten
                //  anymore
                this[view.offset + n] = value
                return true
            }
            if (view[0] == SENTINEL_VALUE) {
                // not found - instead, we append it to the end
                var j = 0
                while (j < n) {
                    this[view.offset + j] = key[j]
                    ++j
                }
                this[view.offset + n] = value
                return true
            }
            view.offset += n + 1
        }
        return false
    }

    /**
     * Removes the given [key] from [this] bucket, returning its original value of type [Int], or [NOT_FOUND] if it's
     *  not present.
     */
    private inline fun IntArray.remove(key: IntCollectionView): Int {
        val index = findKey(key)
        if (index == -1) {
            return NOT_FOUND
        } else {
            val value = this[index + n]
            // if this is not the last element in the bucket, we overwrite the targeted value by this last element in
            //  this bucket, and mark that last element spot as free
            if (index + n + 1 >= size || this[index + n + 1] == SENTINEL_VALUE) {
                this[index] = SENTINEL_VALUE
            } else {
                // finding the last element, which is guaranteed to be after `index`
                var last = index + n + 1
                while (last < size && this[last] != SENTINEL_VALUE) {
                    last += n + 1
                }
                // going back one, as we're currently pointing one past the last element
                last -= n + 1
                // we can now move the last element's data to our spot, and mark the last element as empty
                var j = 0
                while (j < n) {
                    this[index + j] = this[last + j]
                    ++j
                }
                this[index + n] = this[last + n]
                // it suffices to overwrite the very first int only
                this[last] = SENTINEL_VALUE
            }
            return value
        }
    }

    /**
     * Returns the hash index for a given [key]. The computed value is a slot, which is guaranteed to be in
     *  the range `0 ..< slotCount`
     */
    private fun hash(key: IntCollectionView): Int {
//        require(key.size == n)
        var result = 0
        key.forEach {
            result = result * 33 + it
        }
        // bucket size is guaranteed to be a power of two, so '-1' results in a mask of all valid slots which we can use
        //  to limit the hash value w/o requiring the use of the `%` operator
        return result and (buckets.size - 1)
    }

    override fun toString() = joinToString(prefix = "{", postfix = "}") { (key, value) -> "$key -> $value" }

    fun debugString() = buildString {
//        appendLine("raw:\n ${buf.joinToString { if (it == SENTINEL_VALUE) "%" else it.toString() }}")
//
//        appendLine("interpreted:")
//        var i = 0
//        fun line(slotIndex: Int) = " slot #$slotIndex: ${buf.createView(slotIndex * (n + 1), size = n)} - ${buf[slotIndex * (n + 1) + n]}"
//        while (i < buf.size) {
//            if (buf[i] == SENTINEL_VALUE) {
//                var j = i + n + 1
//                while (j < buf.size && buf[j] == SENTINEL_VALUE) {
//                    j += n + 1
//                }
//                appendLine(" slot #${i / (n + 1)}..#${j / (n + 1)}: unused")
//                i = j
//            } else {
//                appendLine(line(i / (n + 1)))
//                i += n + 1
//            }
//        }
    }

}
