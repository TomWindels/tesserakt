package dev.tesserakt.sparql.runtime.stream

import dev.tesserakt.sparql.runtime.collection.SimpleFixedShapeMappingArray
import dev.tesserakt.sparql.runtime.evaluation.mapping.BitsetMapping
import dev.tesserakt.sparql.runtime.evaluation.mapping.Mapping
import dev.tesserakt.sparql.util.Cardinality
import dev.tesserakt.util.bitIterator

/**
 * A special variant of the [Stream] type: emitted elements are only valid as long as the next
 *  element (through [Iterator.next]) hasn't been requested!
 */
class FixedShapeMappingMultiJoinStream(
    private val left: SimpleFixedShapeMappingArray,
    private val right: SimpleFixedShapeMappingArray,
): Stream<Mapping> {

    override val cardinality: Cardinality
        get() = right.cardinality * left.cardinality

    // we use the overlap in bindings emitted from both sides to limit the checks we do at join time
    private val overlap = left.bindings and right.bindings
    private val overlapSize = overlap.countOneBits()

    init {
        require(overlapSize != 0)
    }

    override fun hasZeroCardinality(): Boolean {
        return left.size == 0 || right.size == 0
    }

    override fun supportsEfficientIteration(): Boolean {
        return false
    }

    override fun supportsReuse(): Boolean {
        return true
    }

    override fun iterator(): Iterator<Mapping> = object : AbstractIterator<Mapping>() {

        private val leftArr = left.backing
        private val rightArr = right.backing
        private val leftStepSize = left.bindings.countOneBits()
        private val rightStepSize = right.bindings.countOneBits()
        private var leftOffset = 0
        // see `computeNext()`
        private var rightOffset = -rightStepSize

        private val result = BitsetMapping(
            bindings = left.bindings or right.bindings,
            terms = IntArray((left.bindings or right.bindings).countOneBits())
        )

        /**
         * Lookup table of the indexes that need to be checked from the left stream
         */
        private val lutLeft = run {
            val overlapIter = overlap.bitIterator()
            IntArray(overlapSize) {
                val target = overlapIter.nextInt()
                // see `BitsetMapping::bindingIndex()`
                (((1 shl target) - 1) and left.bindings).countOneBits()
            }
        }

        /**
         * Lookup table of the indexes that need to be checked from the right stream
         */
        private val lutRight = run {
            val overlapIter = overlap.bitIterator()
            IntArray(overlapSize) {
                val target = overlapIter.nextInt()
                // see `BitsetMapping::bindingIndex()`
                (((1 shl target) - 1) and right.bindings).countOneBits()
            }
        }

        /**
         * Lookup table of the source (0: take left, 1: take right, 2: take either) the next term comes
         *  from (see [tryJoin])
         */
        private val mergeOrder = run {
            val union = left.bindings or right.bindings
            val unionIter = union.bitIterator()
            ByteArray(union.countOneBits()) {
                val bindingIndex = unionIter.next()
                val isInLeft = left.bindings and (1 shl bindingIndex) != 0
                val isInRight = right.bindings and (1 shl bindingIndex) != 0
                when {
                    isInLeft && isInRight -> 2
                    isInLeft -> 0
                    isInRight -> 1
                    // cannot happen; union binding means it is part of at least one
                    else -> throw IllegalStateException()
                }
            }
        }

        /**
         * Tries to join the [left] and [right] mappings, with term values starting at [leftOffset] and [rightOffset]
         *  respectively, writing the result to [result] if successful (and returning `true`). Fails if the two
         *  mappings are not compatible (returning `false`)
         */
        private fun tryJoin(): Boolean {
            repeat(overlapSize) { i ->
                if (leftArr[leftOffset + lutLeft[i]] != rightArr[rightOffset + lutRight[i]]) {
                    return false
                }
            }
            joinUnchecked()
            return true
        }

        private fun joinUnchecked() {
            var i = 0
            var leftI = leftOffset
            var rightI = rightOffset
            mergeOrder.iterator().forEach { source ->
                result.terms[i++] = when (source) {
                    0.toByte() -> {
                        leftArr[leftI++]
                    }
                    1.toByte() -> {
                        rightArr[rightI++]
                    }
                    2.toByte() -> {
                        // it was also part of the right side, so we need to increment that one as well
                        ++rightI
                        leftArr[leftI++]
                    }
                    else -> {
                        // malformed setup
                        throw IllegalStateException()
                    }
                }
            }
        }

        override fun computeNext() {
            // we first shift the index as we are currently pointing at the old one
            do {
                rightOffset += rightStepSize
                if (rightOffset >= right.backing.size) {
                    leftOffset += leftStepSize
                    if (leftOffset >= left.backing.size) {
                        done()
                        return
                    }
                    // we successfully shifted the left side over, so we can reset the right stream position
                    rightOffset = 0
                }
            } while (!tryJoin())

            setNext(result)
        }

    }

}
