package dev.tesserakt.sparql.runtime.collection

import dev.tesserakt.sparql.runtime.evaluation.BindingIdentifier
import dev.tesserakt.sparql.runtime.evaluation.BindingIdentifierSet
import dev.tesserakt.sparql.runtime.evaluation.TermIdentifier
import dev.tesserakt.sparql.runtime.evaluation.context.QueryContext
import dev.tesserakt.sparql.runtime.evaluation.mapping.Mapping
import dev.tesserakt.sparql.runtime.stream.OptimisedStream
import dev.tesserakt.sparql.runtime.stream.chain
import dev.tesserakt.sparql.runtime.stream.emptyStream
import dev.tesserakt.sparql.util.Cardinality
import kotlin.jvm.JvmInline

/**
 * An array useful for storing a series of mappings, capable of joining with other mappings using the hash join
 *  algorithm. Hash tables are created for every binding name passed in the constructor.
 */
class MultiHashMappingArray(
    // the binding set associated with the index
    private val indexBindingSet: BindingIdentifierSet
): MappingArray {

    private class Bucket(
        /**
         * An ID value for this bucket, unique. Subsequent nodes obtained through [match] are guaranteed to have a
         *  smaller ID value. Serves as a hint when traversing multiple index constraints through [match] links
         */
        val id: Int,
        /**
         * A list of other bucket elements, with the index matching this binding index representing a shared term value.
         * Example:
         *  `match = [ Bucket2, Bucket3, null ]`
         *  reflects the following scenario:
         *  * this mapping array is indexed on three bindings
         *  * the previous bucket with the same term value for the first indexed binding is Bucket2
         *  * the previous bucket with the same term value for the second indexed binding is Bucket3
         *  * there is no earlier bucket in the list that has a matching value for the third indexed binding
         */
        val match: Array<Bucket?>,
        /**
         * The previous linked-list bucket entry; has no content similarities based on this relation alone
         */
        var previous: Bucket?,
        /**
         * The next linked-list bucket entry; has no content similarities based on this relation alone
         */
        var next: Bucket?,
        /**
         * All mappings with identical values for the [indexBindingSet]
         */
        val mappings: SimpleMappingArray,
    ) {
        override fun toString() = "Bucket(id:$id, previous: ${match.joinToString(prefix = "[", postfix = "]")}, cardinality ${mappings.cardinality}, ${mappings.iter().firstOrNull()})"

        override fun equals(other: Any?): Boolean {
            return other is Bucket && id == other.id
        }

        override fun hashCode(): Int {
            return id
        }
    }

    @JvmInline
    private value class Constraints(val value: List<Entry>): List<Constraints.Entry> by value {

        data class Entry(
            /**
             * Index into the [indexBindingSet]
             */
            val indexedBindingIndex: Int,
            val targetTermValue: TermIdentifier,
        ) {
            override fun toString() = "binding #$indexedBindingIndex, value ${targetTermValue.id}"
        }

        override fun toString(): String = value.joinToString(prefix = "Constraints {", postfix = "}")

        companion object {
            val EMPTY = Constraints(emptyList())
        }

    }

    constructor(
        context: QueryContext,
        bindings: Set<String>
    ): this(indexBindingSet = BindingIdentifierSet(context, bindings))

    /**
     * The highest unused ID value, set as ID for the next bucket
     */
    // TODO: this ID has to shift back based on deletions periodically, which can be achieved
    //  by going through all buckets (starting from the tail) and resetting the ID value to their respective index in
    //  that chain
    private var id = Int.MAX_VALUE

    /**
     * A collection of linked list tails, used to find the last bucket matching a (set of) constraint(s). Example:
     *  ```
     *  tail = [
     *      {
     *          term1 => Bucket1,
     *          term2 => Bucket2,
     *      },
     *      {
     *          term3 => Bucket2,
     *          term4 => Bucket3,
     *      }
     *  ]
     *  ```
     *  Means that the first indexed binding with associated value term1 has its last bucket being instance `Bucket1`
     */
    private val tail = List(indexBindingSet.size) {
        mutableMapOf<TermIdentifier, Bucket>()
    }

    /**
     * The last bucket of the linked list, used for regular iteration
     */
    private var last: Bucket? = null

    init {
        check(indexBindingSet.size > 0) { "Invalid use of MultiHashMappingArray! No bindings are used!" }
    }

    override var cardinality = Cardinality(0)
        private set

    override val indexes: BindingIdentifierSet
        get() = indexBindingSet

    override fun iter(mapping: Mapping): OptimisedStream<Mapping> {
        val constraints = createConstraints(mapping)
        return iter(constraints)
    }

    override fun iter(mappings: List<Mapping>): List<OptimisedStream<Mapping>> {
        val subsets = mappings.map { createConstraints(it) }
        // identical subsets can reuse the same result
        val cached = mutableMapOf<Constraints, OptimisedStream<Mapping>>()
        return subsets.map { subset ->
            cached.getOrPut(subset) { iter(subset) }
        }
    }

    private fun iter(constraints: Constraints): OptimisedStream<Mapping> {
        if (constraints.size == indexBindingSet.size) {
            // we only expect a single bucket to match at most
            val bucket = firstBucketMatching(constraints)
            return bucket?.mappings?.iter() ?: emptyStream()
        }
        var currentBucket = firstBucketMatching(constraints)
        if (currentBucket == null) {
            return emptyStream()
        }
        var stream: OptimisedStream<Mapping> = currentBucket.mappings.iter()
        currentBucket = priorBucket(currentBucket, constraints)
        while (currentBucket != null) {
            stream = stream.chain(currentBucket.mappings.iter())
            currentBucket = priorBucket(currentBucket, constraints)
        }
        return stream
    }

    override fun iter(): OptimisedStream<Mapping> {
        var result: OptimisedStream<Mapping> = emptyStream()
        var currentBucket = last
        while (currentBucket != null) {
            result = result.chain(currentBucket.mappings.iter())
            currentBucket = currentBucket.previous
        }
        return result
    }

    /**
     * Adds a mapping to the backing array and indexes it accordingly.
     */
    override fun add(mapping: Mapping) {
        val constraints = createConstraints(mapping)
        var currentBucket = firstBucketMatching(constraints)
        if (currentBucket == null) {
            currentBucket = addBucket(constraints)
        }
        currentBucket.mappings.add(mapping)
        cardinality += 1
    }

    /**
     * Adds all mappings to the backing array and indexes it accordingly.
     */
    override fun addAll(mappings: Iterable<Mapping>) {
        mappings.forEach { add(it) }
    }

    override fun remove(mapping: Mapping) {
        val constraints = createConstraints(mapping)
        var found = false
        removeFirstMatchingBucketIf(constraints) { bucket ->
            found = true
            bucket.mappings.remove(mapping)
            bucket.mappings.size == 0
        }
        if (!found) {
            throw NoSuchElementException("No bucket found matching $constraints (array: $this)")
        }
        cardinality -= 1
    }

    override fun removeAll(mappings: Iterable<Mapping>) {
        mappings.forEach { remove(it )}
    }

    override fun toString(): String =
        "MultiHashMappingArray (cardinality $cardinality, indexed on ${indexBindingSet}, ${countBuckets()} bucket(s))"

    /* helpers */

    private fun createConstraints(reference: Mapping): Constraints {
        val constraints = indexBindingSet.asIntIterable().mapIndexedNotNull { index, bindingId ->
            val term = reference.get(BindingIdentifier(bindingId))
                ?: return@mapIndexedNotNull null
            Constraints.Entry(index, term)
        }
        if (constraints.isEmpty()) {
            return Constraints.EMPTY
        }
        return Constraints(constraints)
    }

    /**
     * Removes the first [Bucket] matching the given [constraints] if [predicate] using the retrieved
     *  instance returns `true`.
     */
    private inline fun removeFirstMatchingBucketIf(
        constraints: Constraints,
        predicate: (Bucket) -> Boolean
    ) {
        val positions = Array<Bucket?>(constraints.size) { null }
        val bucket = settleNextBucket(constraints, positions) ?: return
        // with our bucket found and the positions all pointing to the one prior in the collection,
        //  we can process the removal, if necessary
        if (!predicate(bucket)) {
            return
        }
        constraints.forEachIndexed { index, entry ->
            val previous = positions[index]
            // all positional buckets now have to be updated: if they exist, that means their `previous` value has to be
            //  updated to point to the `previous` of the bucket being removed
            if (previous != null) {
                previous.match[entry.indexedBindingIndex] = bucket.match[entry.indexedBindingIndex]
            }
            // otherwise, we have to update the tail, letting it point to our `previous` value
            else {
                val prior = bucket.match[entry.indexedBindingIndex]
                if (prior == null) {
                    tail[entry.indexedBindingIndex].remove(entry.targetTermValue)
                } else {
                    tail[entry.indexedBindingIndex][entry.targetTermValue] = prior
                }
            }
        }
        // we also have to update the regular linked list chain, so it no longer contains this bucket
        // covering the special case first
        if (last == bucket) {
            last = bucket.previous
            // we can't be leaking ourselves here
            bucket.previous?.next = null
        } else {
            bucket.next?.previous = bucket.previous
            bucket.previous?.next = bucket.next
        }
    }

    /**
     * Finds the first bucket matching the provided [constraints], or `null` if no such bucket exists.
     * Note that empty [constraints] will result in [last] if there are buckets present (as all buckets match).
     */
    private fun firstBucketMatching(constraints: Constraints): Bucket? {
        if (constraints.isEmpty()) {
            return last
        }
        // special case, meaning that a single start lookup suffices
        if (constraints.size == 1) {
            val entry = constraints[0]
            return tail[entry.indexedBindingIndex][entry.targetTermValue]
        }
        // we can obtain an initial start location into the backing array
        val buckets = Array<Bucket?>(constraints.size) { null }
        return settleNextBucket(constraints, buckets)
    }

    /**
     * Starts from the given bucket index [start], returning the next bucket index matching the given [constraints],
     *  or `-1` if no such bucket exists
     */
    private fun priorBucket(start: Bucket, constraints: Constraints): Bucket? {
        if (constraints.isEmpty()) {
            return start.previous
        }
        // special case, meaning that a single start lookup suffices
        if (constraints.size == 1) {
            val entry = constraints[0]
            return start.match[entry.indexedBindingIndex]
        }
        // as we settle before the next position, our initial start bucket is the first position
        //  to considered
        val buckets = Array<Bucket?>(constraints.size) { start }
        return settleNextBucket(constraints, buckets)
    }

    /**
     * Starts from the given position [buckets], advancing the various positions until they
     *  all point to the same [Bucket.match] value according to their relative position in the [constraints] set,
     *  and returns that consensus bucket (or `null` if not possible, leaving the [buckets] in an undefined state)
     */
    private fun settleNextBucket(
        constraints: Constraints,
        buckets: Array<Bucket?>
    ): Bucket? {
        // we can now let it advance until reaching a consensus on the next bucket
        if (!settleBucketsBeforeNext(constraints, buckets)) {
            return null
        }
        val prior = buckets[0]
        // technically not possible to be `null`
        return if (prior == null) {
            // there is no prior position, so it's the first constraint's first tail element
            tail[constraints[0].indexedBindingIndex][constraints[0].targetTermValue]
        } else {
            prior.match[constraints[0].indexedBindingIndex]
        }
    }

    /**
     * Traverses [Bucket.match] [Bucket]s until settled (= all pointing to **one before** the same [Bucket] instance)
     *  while satisfying the [constraints]. Returns `true` if a good state was reached
     */
    private fun settleBucketsBeforeNext(
        constraints: Constraints,
        buckets: Array<Bucket?>,
    ): Boolean {
        check(constraints.size == buckets.size) { "Bad usage: constraints.size == buckets.size (was ${constraints.size}, ${buckets.size})" }
        // all positions are valid, but they might not point to the same bucket, in which case we haven't found
        //  an exact match yet
        while (true) {
            // checking to see if all positions match, in which case we obtained our result
            var i = 1
            val next1 = run {
                val b = buckets[0]
                if (b != null) {
                    // looking one further than our current position
                    b.match[constraints[0].indexedBindingIndex]
                        ?: return false
                } else {
                    // or alternatively looking at the first element according to the tail
                    tail[constraints[0].indexedBindingIndex][constraints[0].targetTermValue]
                        ?: return false
                }
            }
            while (i < buckets.size) {
                val next2 = run {
                    val b = buckets[i]
                    if (b != null) {
                        // looking one further than our current position
                        b.match[constraints[i].indexedBindingIndex]
                            ?: return false
                    } else {
                        // or alternatively looking at the first element according to the tail
                        tail[constraints[i].indexedBindingIndex][constraints[i].targetTermValue]
                            ?: return false
                    }
                }
                if (next1.id < next2.id) {
                    // position[0] has to move to the next link as it's ID is too small to satsify
                    //  other constraints
                    buckets[0] = next1
                    // we have to start from the top again
                    break
                } else if (next1.id > next2.id) {
                    // position[i] has to move to the next link
                    buckets[i] = next2
                    // we have to start from the top again
                    break
                }
                ++i
            }
            // if we reached the end of the loop organically, all individual positions settled to the same bucket (ID)
            if (i >= buckets.size) {
                return true
            }
        }
    }

    /**
     * Creates a new bucket satisfying the [constraints], updating the various links in other buckets, the [last] bucket
     *  entry and the [tail] state, returning this new instance
     */
    private fun addBucket(constraints: Constraints): Bucket {
        // we only allow full matches to make up a bucket; this is required for the bucket offsets based on binding index!
        check(constraints.size == indexBindingSet.size) { "Invalid constraint set used during bucket creation!" }
        val new = Bucket(
            id = this.id--,
            // we have no next neighbours as we're the newest bucket
            match = Array(indexBindingSet.size) { null },
            previous = last,
            next = null,
            mappings = SimpleMappingArray(),
        )
        // also updating the last bucket
        check(last?.next == null) { "Structural error, last element ${last} contains a next element" }
        last?.next = new
        // we have to update the tail for all our constraints, making sure it points to us now; the original tail
        //  value becomes our 'previous' entry, or indicate there is no such bucket before us
        constraints.forEach { entry ->
            val original = tail[entry.indexedBindingIndex].put(entry.targetTermValue, new)
            if (original != null) {
                new.match[entry.indexedBindingIndex] = original
            }
        }
        last = new
        return new
    }

    /**
     * Counts the number of buckets that can be obtained, starting from [last]. This is a debug
     *  method (e.g. string representation); so the count is not cached/tracked and is instead calculated
     */
    private fun countBuckets(): Int {
        var count = 0
        var last = last
        while (last != null) {
            ++count
            last = last.previous
        }
        return count
    }

}
