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
         * A list of indexes to go to subsequent elements in the flattened collection, with the index matching
         *  this binding index.
         * Example:
         *  `next = [ 2, 3, -1 ]`
         *  reflects the following scenario:
         *  * this mapping array is indexed on three bindings
         *  * the previous bucket with the same term value for the first indexed binding is offset by 2
         *  * the previous bucket with the same term value for the second indexed binding is offset by 3
         *  * there is no earlier bucket in the list that has a matching value for the third indexed binding
         */
        val previous: IntArray,
        /**
         * All mappings with identical values for the [indexBindingSet]
         */
        val mappings: SimpleMappingArray,
    ) {
        override fun toString() = "Bucket(previous: ${previous.joinToString(prefix = "[", postfix = "]")}, cardinality ${mappings.cardinality}, ${mappings.iter().firstOrNull()})"
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
     * A collection of end points, used to find the last bucket matching a (set of) constraint(s). Example:
     *  ```
     *  tail = [
     *      {
     *          term1 => 1,
     *          term2 => 2,
     *      },
     *      {
     *          term3 => 2,
     *          term4 => 3,
     *      }
     *  ]
     *  ```
     *  Means that the first indexed binding with associated value term1 has its last bucket at index 1 in
     *   the [buckets] list
     */
    private val tail = List(indexBindingSet.size) {
        mutableMapOf<TermIdentifier, Int>()
    }

    /**
     * All buckets associated with this mapping array; the traversal is done using the [tail] and [Bucket.previous]
     *  described above
     */
    private val buckets = mutableListOf<Bucket>()

    init {
        check(indexBindingSet.size > 0) { "Invalid use of MultiHashMappingArray! No bindings are used!" }
    }

    override var cardinality = Cardinality(0)
        private set

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
            val bucketIndex = firstBucketIndexOf(constraints)
            return if (bucketIndex >= 0) {
                buckets[bucketIndex].mappings.iter()
            } else {
                emptyStream()
            }
        }
        var bucketIndex = firstBucketIndexOf(constraints)
        if (bucketIndex < 0) {
            return emptyStream()
        }
        var stream: OptimisedStream<Mapping> = buckets[bucketIndex].mappings.iter()
        bucketIndex = nextBucketIndexOf(bucketIndex, constraints)
        while (bucketIndex >= 0) {
            stream = stream.chain(buckets[bucketIndex].mappings.iter())
            bucketIndex = nextBucketIndexOf(bucketIndex, constraints)
        }
        return stream
    }

    override fun iter(): OptimisedStream<Mapping> {
        var result: OptimisedStream<Mapping> = emptyStream()
        val iter = buckets.iterator()
        while (iter.hasNext()) {
            result = result.chain(iter.next().mappings.iter())
        }
        return result
    }

    /**
     * Adds a mapping to the backing array and indexes it accordingly.
     */
    override fun add(mapping: Mapping) {
        val constraints = createConstraints(mapping)
        var bucketIndex = firstBucketIndexOf(constraints)
        if (bucketIndex == -1) {
            bucketIndex = addBucket(constraints)
        }
        buckets[bucketIndex].mappings.add(mapping)
        cardinality += 1
    }

    /**
     * Adds all mappings to the backing array and indexes it accordingly.
     */
    override fun addAll(mappings: Iterable<Mapping>) {
        mappings.forEach { add(it )}
    }

    override fun remove(mapping: Mapping) {
        val constraints = createConstraints(mapping)
        val bucketIndex = firstBucketIndexOf(constraints)
        if (bucketIndex == -1) {
            throw NoSuchElementException("No bucket found matching $constraints (array: $this)")
        }
        buckets[bucketIndex].mappings.remove(mapping)
        cardinality -= 1
    }

    override fun removeAll(mappings: Iterable<Mapping>) {
        mappings.forEach { remove(it )}
    }

    override fun toString(): String =
        "MultiHashMappingArray (cardinality $cardinality, indexed on ${indexBindingSet}, ${buckets.size} bucket(s))"

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
     * Finds the first bucket index matching the provided [constraints], or `-1` if no such bucket exists.
     * Note that empty [constraints] will result in index `0` if there are buckets present (as all buckets match).
     */
    private fun firstBucketIndexOf(constraints: Constraints): Int {
        if (buckets.isEmpty()) {
            return -1
        }
        if (constraints.isEmpty()) {
            return 0
        }
        // special case, meaning that a single start lookup suffices
        if (constraints.size == 1) {
            val entry = constraints[0]
            return tail[entry.indexedBindingIndex][entry.targetTermValue] ?: -1
        }
        // we can obtain an initial start location into the backing array
        val positions = IntArray(constraints.size) { index ->
            val entry = constraints[index]
            tail[entry.indexedBindingIndex][entry.targetTermValue]
                // no start value associated with this binding + term combination means there are no buckets
                //  available that satisfy this constraint
                ?: return -1
        }
        return settledPriorPositionOf(constraints, positions)
    }

    /**
     * Starts from the given bucket index [start], returning the next bucket index matching the given [constraints],
     *  or `-1` if no such bucket exists
     */
    private fun nextBucketIndexOf(start: Int, constraints: Constraints): Int {
        if (constraints.isEmpty()) {
            val next = start + 1
            return if (next < buckets.size) {
                next
            } else {
                -1
            }
        }
        val currentBucket = buckets[start]
        // having all positions shift one over to begin with
        val positions = IntArray(constraints.size) { index ->
            val entry = constraints[index]
            val increment = currentBucket.previous[entry.indexedBindingIndex]
            if (increment <= 0) {
                return -1
            }
            start - increment
        }
        // we can now let these offset positions yield a single settled position
        return settledPriorPositionOf(constraints, positions)
    }

    /**
     * Decrements all individual [positions] until settled and while satisfying the [constraints].
     */
    private fun settledPriorPositionOf(
        constraints: Constraints,
        positions: IntArray,
    ): Int {
        check(constraints.isNotEmpty())
        // all positions are valid, but they might not point to the same bucket, in which case we haven't found
        //  an exact match yet
        while (true) {
            // checking to see if all positions match, in which case we obtained our result
            val ref = positions[0]
            var i = 1
            while (i < positions.size) {
                val current = positions[i]
                if (ref != current) {
                    if (ref > current) {
                        // ref (position[0]) has to be incremented using the neighbour from our current bucket
                        //  at the corresponding indexedBindingIndex
                        val increment = buckets[ref].previous[constraints[0].indexedBindingIndex]
                        if (increment <= 0) {
                            // there's no neighbour
                            return -1
                        }
                        positions[0] = ref - increment
                    } else {
                        // position[i] has to be incremented
                        val increment = buckets[current].previous[constraints[i].indexedBindingIndex]
                        if (increment <= 0) {
                            // there's no neighbour
                            return -1
                        }
                        positions[i] = current - increment
                    }
                    // we have to start from the top again
                    break
                }
                ++i
            }
            // if we reached the end of the loop organically, all individual positions settled to the same bucket
            if (i >= positions.size) {
                return ref
            }
        }
    }

    /**
     * Creates a new bucket satisfying the [constraints] into the [buckets] collection, and returns its index
     */
    private fun addBucket(constraints: Constraints): Int {
        // we only allow full matches to make up a bucket; this is required for the bucket offsets based on binding index!
        check(constraints.size == indexBindingSet.size) { "Invalid constraint set used during bucket creation!" }
        val new = Bucket(
            // we have no next neighbours as we're the newest bucket
            previous = IntArray(indexBindingSet.size) { -1 },
            mappings = SimpleMappingArray(),
        )
        val index = buckets.size
        buckets.add(new)
        // we have to update the tail for all our constraints, making sure it points to us now; the original tail
        //  value becomes our 'previous' entry, or indicate there is no such bucket before us
        constraints.forEach { entry ->
            val original = tail[entry.indexedBindingIndex].put(entry.targetTermValue, index)
            if (original != null) {
                new.previous[entry.indexedBindingIndex] = index - original
            }
        }
        return index
    }

}
