package dev.tesserakt.sparql.runtime.collection.integer

import dev.tesserakt.sparql.runtime.collection.integer.IntHashMap.Companion.NOT_FOUND


class IntHashMapBak(
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

    init {
        require(n > 0)
    }

    private val slotMask = run {
        // the slot count is a power of two, smaller than capacity;
        // this allows the hashing to be done using bit shifts, and the higher capacity allows for hashes near the end
        //  to still be appended for a while longer
        val slotCount = (capacity - 1).takeHighestOneBit()
        // its mask is simply one less; a slot count of 4 = 0b0100, mask = 0b0011
        slotCount - 1
    }

    /**
     * The actual buffer used to store the entire map (which is always flattened).
     * The elements are always stored in increasing hash value, with the hash value itself being the smallest possible
     *  index it can be placed at.
     * Example buffer state, with number X in 0..9 denoting a K-V pair (n+1 ints in size) populated with a given hash X,
     *  and % denoting a free spot:
     * ```
     * [ % 1 1 2 3 3 3 7 % 9 ]
     * ```
     * Key properties that can be seen in this example:
     * * The very first spot is free, as no K-V pairs with hash value 0 has been inserted
     * * The second K-V pair hash 1 caused a hash collision, resulting in K-V hash 2 and all instances of K-V hash 3
     *  to be shifted over
     * * Element K-V pair hash 7 is at its intended location, so it won't be moved in any circumstance
     * * The second to last spot is free instead of being taken up by K-V #9, as its hash value is 9, and the free spot
     *  is intended for K-V pairs with hash value 8 (or lower in case of hash collisions)
     *
     * Removal of element at slot #1 would require multiple shifts due to the significant amount of hash collisions:
     * * The second K-V pair with hash value #1 takes its place
     * * The K-V pair with hash value 2 goes one spot to the left
     * * The last K-V pair with hash value 3 takes up the spot originally occupied by K-V hash 2
     * * The original position of the last K-V pair is marked as free
     * * K-V pairs hash 7 and 9 remain unchanged: their index already matches their hash value
     */
    private val buf = IntArray(capacity * (n + 1)) { SENTINEL_VALUE }

    /**
     * Sets the provided [value] for the given [key]. Returns `false` if unsuccessful (which happens once
     *  the [key] value is not currently present in the map, and the number of hash collisions would cause
     *  a key-value pair to spill outside the internal buffer)
     */
    operator fun set(key: IntCollectionView, value: Int): Boolean {
        findKey(
            key = key,
            onKeyFound = { loc ->
                // no need to override the key value
                buf[loc + n] = value
            },
            onSentinelReached = { loc ->
                // we can simply override this free spot
                key.forEachIndexed { offset, k -> buf[loc + offset] = k }
                buf[loc + n] = value
            },
            onHashRegionEndReached = { loc ->
                if (!moveEntriesRight(start = loc)) {
                    return false
                }
                // we can put our new key-value pair here now
                key.forEachIndexed { offset, k -> buf[loc + offset] = k }
                buf[loc + n] = value
            },
            onBufferEndReached = {
                // value was not added
                return false
            }
        )
        return true
    }

    /**
     * Gets the value associated with the given [key], or special value [NOT_FOUND] if not found
     */
    operator fun get(key: IntCollectionView): Int {
        findKey(
            key = key,
            onKeyFound = { loc ->
                // jumping over the `n` key integers
                return buf[loc + n]
            },
            onSentinelReached = {
                return NOT_FOUND
            },
            onHashRegionEndReached = {
                return NOT_FOUND
            },
            onBufferEndReached = {
                return NOT_FOUND
            }
        )
        // is not reachable, but still has to be added
        return NOT_FOUND
    }

    /**
     * Removes the key-value pair associated with the given [key], returning its currently associated value, or
     *  [NOT_FOUND] if the [key] is not currently present inside the map
     */
    fun remove(key: IntCollectionView): Int {
        var pos = -1
        findKey(
            key = key,
            onKeyFound = { loc -> pos = loc },
            onHashRegionEndReached = { return NOT_FOUND },
            onSentinelReached = { return NOT_FOUND },
            onBufferEndReached = { return NOT_FOUND },
        )
        // as we currently contain the key-value pair, we can return its original value
        val currentValue = buf[pos + n]
        // instead of marking this slot as freed, we need to make sure none of our neighbours have to move over
        //  based on the logic used to lay out the key-value pairs in the buffer
        moveEntriesLeft(pos)
        return currentValue
    }

    override fun iterator(): Iterator<Pair<IntCollectionView, Int>> = object: Iterator<Pair<IntCollectionView, Int>> {

        // TODO fixme this can be done better
        private var next = if (buf[0] == SENTINEL_VALUE) findNextIndex() else 0

        override fun hasNext(): Boolean {
            return next != -1
        }

        override fun next(): Pair<IntCollectionView, Int> {
            val key = buf.createView(start = next, size = n)
            val value = buf[next + n]
            // also consuming it
            next = findNextIndex()
            return key to value
        }

        private fun findNextIndex(): Int {
            var pos = next + n + 1
            while (pos < buf.size && buf[pos] == SENTINEL_VALUE) {
                pos += n + 1
            }
            if (pos >= buf.size) {
                return -1
            }
            return pos
        }

    }

    /* helpers */

    /**
     * Moves all entries starting at [start] (which points to the first key value) one spot to the right, resulting in
     *  the [start] position becoming 'free'.
     *
     * Returns `true` if successful, or `false` if shifting all spots one over would cause a value to spill out of the
     *  buffer.
     *
     * NOTE: it does NOT mark the spot at [start] as freed, as it's assumed the call-site would assume the operation
     *  to succeed upon returning `true`.
     */
    private fun moveEntriesRight(start: Int): Boolean {
//        require(buf[start] != SENTINEL_VALUE)
        // finding the first free spot where we can move elements towards
        val targetPos = run {
            var result = start + n + 1
            while (result < buf.size && buf[result] != SENTINEL_VALUE) {
                result += n + 1
            }
            if (result > buf.size) {
                // no free spots to our right, meaning that we'd spill if attempting to move
                return false
            }
            result
        }
        // checking to see if a single move suffices
        if (targetPos == start + n + 1) {
            copyEntry(start, targetPos)
            return true
        }
        // moving the entire set of values in between start .. targetPos 'n + 1' spots
        // working our way backwards as to not overwrite anything that hasn't moved yet
        var i = targetPos + n
        while (i >= start + n) {
            buf[i] = buf[i - n - 1]
            --i
        }
        return true
        // FIXME: apply the logic below instead of the last step
        // we don't have to move every element between `start` and `targetPos`; instead, we only have to move
        //  the first element of a matching hash group one to its new end, until we reach `start`
        // example: [ 0 1a 1b 2a 2b 2c % ] -> moving start == 1 (1a) -> [ 0 % 1b 1a 2b 2c 2a ]
    }

    /**
     * Moves all values one key-value position to the left, with the first **written** location being the [start]
     *  value (and thus the first moved-from element being [start]` + n + 1`).
     * Elements stop being moved upon reaching an empty spot, or a key-value pair is encountered for which it's hash
     *  value exceeds the position it would move to.
     * The final spot that is being moved *from* is marked as freed.
     */
    private fun moveEntriesLeft(start: Int) {
        if (start + n + 1 >= buf.size) {
            // only marking the `start` slot as empty
            buf[start] = SENTINEL_VALUE
            return
        }
        val view = buf.createView(start = start + n + 1, size = n)
        while (true) {
            if (view[0] == SENTINEL_VALUE) {
                // we'd be copying an empty spot; we can limit the amount we copy to the SENTINEL_VALUE only, and
                //  bail
                buf[view.offset - n - 1] = SENTINEL_VALUE
                return
            }
            // we need to make sure the value we're considering moving over actually wants to move over
            if (hash(view) * (n + 1) > (view.offset - n - 1)) {
                // our current view should not be moved, so the slot to our left gets marked empty instead
                buf[view.offset - n - 1] = SENTINEL_VALUE
                return
            }
            // we can move it one over to the left
            copyEntry(src = view.offset, dst = view.offset - n - 1)
            // checking to see if we're now at the end
            if (view.offset + n + 1 >= buf.size) {
                // as we don't have any neighbours anymore, we can mark this slot, which is the last, as freed
                buf[view.offset] = SENTINEL_VALUE
                return
            }
            // shifting the view slot over
            view.offset += n + 1
        }
    }

    /**
     * Copies an entry (= n + 1 ints) starting from the [src] location into the [dst] location.
     * **Does key-value alignment checks!**
     */
    private inline fun copyEntry(src: Int, dst: Int) {
        var i = 0
        while (i < n + 1) {
            buf[dst + i] = buf[src + i]
            ++i
        }
    }

    /**
     * Searches for a given [key], calling one of the provided callback functions based on the result:
     * * [onKeyFound] in case the provided [key] was found in [buf]
     * * [onSentinelReached] in case an empty spot was encountered whilst scanning through the buffer to find a
     *  matching key (and thus no key with higher hash value has been encountered)
     * * [onHashRegionEndReached] in case a key was reached with a higher hash value, indicating the key is not present
     *  in [buf], with the parameter value being the buffer index pointing to that key's first value
     * * [onBufferEndReached] if the search stopped because of reaching [buf]s end
     */
    private inline fun findKey(
        key: IntCollectionView,
        onKeyFound: (Int) -> Unit,
        onSentinelReached: (Int) -> Unit,
        onHashRegionEndReached: (Int) -> Unit,
        onBufferEndReached: () -> Unit,
    ) {
        if (key.size != n) {
            throw IllegalArgumentException("Key has incorrect size ${key.size}, expected key of size $n")
        }
        val keyHash = hash(key) * (n + 1)
        // finding the matching key, either until one matches by value, or we reach the end with a sentinel value
        // getting a view of the relevant key area
        val view = buf.createView(
            start = keyHash,
            size = n,
        )
        while (true) {
            if (view.first() == SENTINEL_VALUE) {
                // we haven't found a match and reached the end of the search zone
                onSentinelReached(view.offset)
                return
            }
            // calculating the hash first, as viewHash < keyHash cases skip the `==` check, and allow for the value
            //  to be reused in the subsequent check
            if (key == view) {
                // we found a match
                onKeyFound(view.offset)
                return
            }
            if (hash(view) * (n + 1) > keyHash) {
                // the key we're currently pointing at is indicative of the possible region having ended
                onHashRegionEndReached(view.offset)
                return
            }
            if (view.offset + n + 1 > buf.size) {
                onBufferEndReached()
                return
            }
            // no offset found, moving the view along (n key ints + 1 value int)
            view.offset += n + 1
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
        return result and slotMask
    }

    override fun toString() = joinToString(prefix = "{", postfix = "}") { (key, value) -> "$key -> $value" }

    fun debugString() = buildString {
        appendLine("raw:\n ${buf.joinToString { if (it == SENTINEL_VALUE) "%" else it.toString() }}")

        appendLine("interpreted:")
        var i = 0
        fun line(slotIndex: Int) = " slot #$slotIndex: ${buf.createView(slotIndex * (n + 1), size = n)} - ${buf[slotIndex * (n + 1) + n]}"
        while (i < buf.size) {
            if (buf[i] == SENTINEL_VALUE) {
                var j = i + n + 1
                while (j < buf.size && buf[j] == SENTINEL_VALUE) {
                    j += n + 1
                }
                appendLine(" slot #${i / (n + 1)}..#${j / (n + 1)}: unused")
                i = j
            } else {
                appendLine(line(i / (n + 1)))
                i += n + 1
            }
        }
    }

}
