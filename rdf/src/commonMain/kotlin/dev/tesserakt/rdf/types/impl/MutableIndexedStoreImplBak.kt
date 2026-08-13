package dev.tesserakt.rdf.types.impl

import dev.tesserakt.rdf.types.IndexedStore
import dev.tesserakt.rdf.types.MutableStore
import dev.tesserakt.rdf.types.Quad

internal class MutableIndexedStoreImplBak: IndexedStore, MutableStore {

    private class QuadEntry(
        /**
         * An ID value for this entry, unique. Subsequent nodes obtained through [match] are guaranteed to have a
         *  smaller ID value. Serves as a hint when traversing multiple index constraints through [match] links
         */
        val id: Int,
        /**
         * A list of other quad entries, with the index in the array representing the quad position of the matching value
         * Example:
         *  `match = [ Entry2, Entry3, null, null ]`
         *  reflects the following scenario:
         *  * the previous entry with the same term value as [Quad.Subject] is `Entry2`
         *  * the previous entry with the same term value as [Quad.Predicate] is `Entry3`
         *  * there is no earlier entry in the store that has a matching value for either [Quad.Object] or [Quad.Graph]
         */
        val match: Array<QuadEntry?>,
        /**
         * The previous linked-list bucket entry; has no content similarities based on this relation alone
         */
        var previous: QuadEntry?,
        /**
         * The next linked-list bucket entry; has no content similarities based on this relation alone
         */
        var next: QuadEntry?,
        /**
         * The actual value of this entry
         */
        val value: Quad,
    ) {
        override fun toString() = "QuadEntry(id:$id, previous: ${match.joinToString(prefix = "[", postfix = "]")}, $value"

        override fun equals(other: Any?): Boolean {
            return other is QuadEntry && id == other.id
        }

        override fun hashCode(): Int {
            return id
        }
    }

    /**
     * The highest unused ID value, set as ID for the next bucket
     */
    // TODO: this ID has to shift back based on deletions periodically, which can be achieved
    //  by going through all buckets (starting from the tail) and resetting the ID value to their respective index in
    //  that chain
    private var id = Int.MAX_VALUE

    /**
     * A collection of linked list tails, used to find the last entry matching a quad value
     */
    private val subjectTail = mutableMapOf<Quad.Subject, QuadEntry>()
    private val predicateTail = mutableMapOf<Quad.Predicate, QuadEntry>()
    private val objectTail = mutableMapOf<Quad.Object, QuadEntry>()
    private val graphTail = mutableMapOf<Quad.Graph, QuadEntry>()

    /**
     * The last bucket of the linked list, used for regular iteration
     */
    private var last: QuadEntry? = null

    override var size = 0
        private set

    override fun iter(s: Quad.Subject?, p: Quad.Predicate?, o: Quad.Object?, g: Quad.Graph?): Iterator<Quad> {
        val s = s?.let { IterEntry(pos = 0, current = subjectTail[s] ?: return EmptyIterator) }
        val p = p?.let { IterEntry(pos = 1, current = predicateTail[p] ?: return EmptyIterator) }
        val o = o?.let { IterEntry(pos = 2, current = objectTail[o] ?: return EmptyIterator) }
        val g = g?.let { IterEntry(pos = 3, current = graphTail[g] ?: return EmptyIterator) }
        val arr = listOfNotNull(s, p, o, g).toTypedArray()
        return when (arr.size) {
            0 -> CompleteIterator(last)
            1 -> SingleConstraintIterator(arr.single())
            else -> MultiConstraintIterator(arr)
        }
    }

    override fun add(element: Quad): Boolean {
        TODO("Not yet implemented")
    }

    override fun clear() {
        TODO("Not yet implemented")
    }

    override fun contains(element: Quad): Boolean {
        TODO("Not yet implemented")
    }

    override fun containsAll(elements: Collection<Quad>): Boolean {
        TODO("Not yet implemented")
    }

    override fun isEmpty(): Boolean {
        TODO("Not yet implemented")
    }

    override fun iterator(): MutableIterator<Quad> {
        TODO("Not yet implemented")
    }

    override fun remove(element: Quad): Boolean {
        TODO("Not yet implemented")
    }

    override fun retainAll(elements: Collection<Quad>): Boolean {
        TODO("Not yet implemented")
    }

    /* iterator implementations */

    private class IterEntry(
        /**
         * The functional position of this entry's constraint.
         * ```
         * 0 => matching subject
         * 1 => matching predicate
         * 2 => matching object
         * 3 => matching graph
         * ```
         */
        val pos: Int,
        var current: QuadEntry,
    )

    private object EmptyIterator: Iterator<Nothing> {
        override fun hasNext(): Boolean = false
        override fun next(): Nothing = throw NoSuchElementException()
    }

    private class CompleteIterator(
        private var current: QuadEntry?,
    ): Iterator<Quad> {

        override fun hasNext(): Boolean {
            return current != null
        }

        override fun next(): Quad {
            val c = current ?: throw NoSuchElementException()
            current = c.previous
            return c.value
        }

    }

    private class SingleConstraintIterator(
        private val entry: IterEntry,
    ): Iterator<Quad> {

        private var hasNext = true

        override fun hasNext(): Boolean {
            return hasNext
        }

        override fun next(): Quad {
            if (!hasNext) {
                throw NoSuchElementException()
            }
            val current = entry.current
            val next = current.match[entry.pos]
            if (next != null) {
                entry.current = next
            } else {
                hasNext = false
            }
            return current.value
        }
    }

    private class MultiConstraintIterator(
        /**
         * Points to the current element to yield
         */
        private val entries: Array<IterEntry>
    ): Iterator<Quad> {

        private var hasNext = settle()

        override fun hasNext(): Boolean {
            return hasNext
        }

        override fun next(): Quad {
            if (!hasNext) {
                throw NoSuchElementException()
            }
            val next = entries[0].current.value
            // advancing the position first
            entries.forEach { entry ->
                val nextEntry = entry.current.match[entry.pos]
                if (nextEntry != null) {
                    entry.current = nextEntry
                } else {
                    // bailing early; updating other items is not necessary, and settling is pointless
                    hasNext = false
                    return next
                }
            }
            // and having it settle again
            hasNext = settle()
            return next
        }

        private fun settle(): Boolean {
            var target = entries[0].current.id
            var mismatch = entries.firstOrNull { it.current.id != target }
            while (mismatch != null) {
                if (target < mismatch.current.id) {
                    mismatch.current = mismatch.current.match[mismatch.pos] ?: return false
                } else {
                    entries[0].current = entries[0].current.match[entries[0].pos] ?: return false
                    target = entries[0].current.id
                }
                mismatch = entries.firstOrNull { it.current.id != target }
            }
            return true
        }

    }

    /* helpers */

//    private inline fun removeQuadEntry(
//        constraints: Constraints,
//        predicate: (Bucket) -> Boolean
//    ) {
//        val positions = Array<Bucket?>(constraints.size) { null }
//        val bucket = settleNextBucket(constraints, positions) ?: return
//        // with our bucket found and the positions all pointing to the one prior in the collection,
//        //  we can process the removal, if necessary
//        if (!predicate(bucket)) {
//            return
//        }
//        constraints.forEachIndexed { index, entry ->
//            val previous = positions[index]
//            // all positional buckets now have to be updated: if they exist, that means their `previous` value has to be
//            //  updated to point to the `previous` of the bucket being removed
//            if (previous != null) {
//                previous.match[entry.indexedBindingIndex] = bucket.match[entry.indexedBindingIndex]
//            }
//            // otherwise, we have to update the tail, letting it point to our `previous` value
//            else {
//                val prior = bucket.match[entry.indexedBindingIndex]
//                if (prior == null) {
//                    tail[entry.indexedBindingIndex].remove(entry.targetTermValue)
//                } else {
//                    tail[entry.indexedBindingIndex][entry.targetTermValue] set prior
//                }
//            }
//        }
//        // we also have to update the regular linked list chain, so it no longer contains this bucket
//        // covering the special case first
//        if (last == bucket) {
//            last = bucket.previous
//            // we can't be leaking ourselves here
//            bucket.previous?.next = null
//        } else {
//            bucket.next?.previous = bucket.previous
//            bucket.previous?.next = bucket.next
//        }
//    }
//
//    /**
//     * Finds the first bucket matching the provided [constraints], or `null` if no such bucket exists.
//     * Note that empty [constraints] will result in [last] if there are buckets present (as all buckets match).
//     */
//    private fun firstBucketMatching(constraints: Constraints): Bucket? {
//        if (constraints.isEmpty()) {
//            return last
//        }
//        // special case, meaning that a single start lookup suffices
//        if (constraints.size == 1) {
//            val entry = constraints[0]
//            return tail[entry.indexedBindingIndex][entry.targetTermValue]
//        }
//        // we can obtain an initial start location into the backing array
//        val buckets = Array<Bucket?>(constraints.size) { null }
//        return settleNextBucket(constraints, buckets)
//    }
//
//    /**
//     * Starts from the given bucket index [start], returning the next bucket index matching the given [constraints],
//     *  or `-1` if no such bucket exists
//     */
//    private fun priorBucket(start: Bucket, constraints: Constraints): Bucket? {
//        if (constraints.isEmpty()) {
//            return start.previous
//        }
//        // special case, meaning that a single start lookup suffices
//        if (constraints.size == 1) {
//            val entry = constraints[0]
//            return start.match[entry.indexedBindingIndex]
//        }
//        // as we settle before the next position, our initial start bucket is the first position
//        //  to considered
//        val buckets = Array<Bucket?>(constraints.size) { start }
//        return settleNextBucket(constraints, buckets)
//    }
//
//    /**
//     * Starts from the given position [buckets], advancing the various positions until they
//     *  all point to the same [Bucket.match] value according to their relative position in the [constraints] set,
//     *  and returns that consensus bucket (or `null` if not possible, leaving the [buckets] in an undefined state)
//     */
//    private fun settleNextBucket(
//        constraints: Constraints,
//        buckets: Array<Bucket?>
//    ): Bucket? {
//        // we can now let it advance until reaching a consensus on the next bucket
//        if (!settleBucketsBeforeNext(constraints, buckets)) {
//            return null
//        }
//        val prior = buckets[0]
//        // technically not possible to be `null`
//        return if (prior == null) {
//            // there is no prior position, so it's the first constraint's first tail element
//            tail[constraints[0].indexedBindingIndex][constraints[0].targetTermValue]
//        } else {
//            prior.match[constraints[0].indexedBindingIndex]
//        }
//    }
//
//    /**
//     * Traverses [Bucket.match] [Bucket]s until settled (= all pointing to **one before** the same [Bucket] instance)
//     *  while satisfying the [constraints]. Returns `true` if a good state was reached
//     */
//    private fun settleBucketsBeforeNext(
//        constraints: Constraints,
//        buckets: Array<Bucket?>,
//    ): Boolean {
//        check(constraints.size == buckets.size) { "Bad usage: constraints.size == buckets.size (was ${constraints.size}, ${buckets.size})" }
//        // all positions are valid, but they might not point to the same bucket, in which case we haven't found
//        //  an exact match yet
//        while (true) {
//            // checking to see if all positions match, in which case we obtained our result
//            var i = 1
//            val next1 = run {
//                val b = buckets[0]
//                if (b != null) {
//                    // looking one further than our current position
//                    b.match[constraints[0].indexedBindingIndex]
//                        ?: return false
//                } else {
//                    // or alternatively looking at the first element according to the tail
//                    tail[constraints[0].indexedBindingIndex][constraints[0].targetTermValue]
//                        ?: return false
//                }
//            }
//            while (i < buckets.size) {
//                val next2 = run {
//                    val b = buckets[i]
//                    if (b != null) {
//                        // looking one further than our current position
//                        b.match[constraints[i].indexedBindingIndex]
//                            ?: return false
//                    } else {
//                        // or alternatively looking at the first element according to the tail
//                        tail[constraints[i].indexedBindingIndex][constraints[i].targetTermValue]
//                            ?: return false
//                    }
//                }
//                if (next1.id < next2.id) {
//                    // position[0] has to move to the next link as it's ID is too small to satsify
//                    //  other constraints
//                    buckets[0] = next1
//                    // we have to start from the top again
//                    break
//                } else if (next1.id > next2.id) {
//                    // position[i] has to move to the next link
//                    buckets[i] = next2
//                    // we have to start from the top again
//                    break
//                }
//                ++i
//            }
//            // if we reached the end of the loop organically, all individual positions settled to the same bucket (ID)
//            if (i >= buckets.size) {
//                return true
//            }
//        }
//    }
//
//    /**
//     * Creates a new bucket satisfying the [constraints], updating the various links in other buckets, the [last] bucket
//     *  entry and the [tail] state, returning this new instance
//     */
//    private fun addBucket(constraints: Constraints): Bucket {
//        // we only allow full matches to make up a bucket; this is required for the bucket offsets based on binding index!
//        check(constraints.size == indexBindingSet.size) { "Invalid constraint set used during bucket creation!" }
//        val new = Bucket(
//            id = this.id--,
//            // we have no next neighbours as we're the newest bucket
//            match = Array(indexBindingSet.size) { null },
//            previous = last,
//            next = null,
//            mappings = SimpleMappingArray(),
//        )
//        // also updating the last bucket
//        check(last?.next == null) { "Structural error, last element $last contains a next element" }
//        last?.next = new
//        // we have to update the tail for all our constraints, making sure it points to us now; the original tail
//        //  value becomes our 'previous' entry, or indicate there is no such bucket before us
//        constraints.forEach { entry ->
//            val original = tail[entry.indexedBindingIndex].put(entry.targetTermValue, new)
//            if (original != null) {
//                new.match[entry.indexedBindingIndex] = original
//            }
//        }
//        last = new
//        return new
//    }
//
//    /**
//     * Counts the number of buckets that can be obtained, starting from [last]. This is a debug
//     *  method (e.g. string representation); so the count is not cached/tracked and is instead calculated
//     */
//    private fun countBuckets(): Int {
//        var count = 0
//        var last = last
//        while (last != null) {
//            ++count
//            last = last.previous
//        }
//        return count
//    }

}
