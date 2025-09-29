package dev.tesserakt.sparql.runtime.stream

import dev.tesserakt.sparql.runtime.collection.GrowingIntArray
import dev.tesserakt.sparql.runtime.collection.VariableWidthBitsetMappingCollection
import dev.tesserakt.sparql.runtime.evaluation.mapping.BitsetMapping
import dev.tesserakt.sparql.util.Cardinality
import dev.tesserakt.util.bitIterator
import kotlin.jvm.JvmInline

@JvmInline
value class BitsetMappingStream(
    private val buf: VariableWidthBitsetMappingCollection
): OptimisedStream<BitsetMapping> {

    override val cardinality: Cardinality
        get() = Cardinality(buf.size)

    override fun supportsReuse(): Boolean {
        return true
    }

    override val description: String
        get() = "BitsetMappingStream(cardinality=$cardinality)"

    override fun iterator() = buf.iterator()

    companion object {
        fun join(one: BitsetMappingStream, two: BitsetMappingStream): BitsetMappingStream {
            val result = VariableWidthBitsetMappingCollection()
            var i = 0
            var j = 0
            val one = one.buf.buf
            val two = two.buf.buf
            while (i < one.size) {
                while (j < two.size) {
                    tryJoin(
                        one = one,
                        two = two,
                        oneIndex = i,
                        twoIndex = j,
                        target = result,
                    )
                    // as `j` points at the bitset mapping start of buffer `two`, `two[j]` is such a bitmask, indicating
                    //  the number of 1s all represent a mapping that has to be jumped over
                    j += two[j].countOneBits() + 1
                }
                j = 0
                // as `i` points at the bitset mapping start of buffer `one`, `one[i]` is such a bitmask, indicating
                //  the number of 1s all represent a mapping that has to be jumped over
                i += one[i].countOneBits() + 1
            }
            return BitsetMappingStream(result)
        }

        private inline fun tryJoin(
            one: GrowingIntArray,
            two: GrowingIntArray,
            oneIndex: Int,
            twoIndex: Int,
            target: VariableWidthBitsetMappingCollection
        ) {
            val bindingsOne = one[oneIndex]
            val bindingsTwo = two[twoIndex]

            val oneSize = bindingsOne.countOneBits()
            val twoSize = bindingsTwo.countOneBits()

            if (bindingsOne == 0) {
                // `two` can be inserted in its entirety
                target.insert { buf ->
                    buf.add(bindingsTwo)
                    repeat(twoSize) { index ->
                        buf.add(two[twoIndex + 1 + index])
                    }
                }
                return
            }
            if (bindingsTwo == 0) {
                // `one` can be inserted in its entirety
                target.insert { buf ->
                    buf.add(bindingsOne)
                    repeat(oneSize) { index ->
                        buf.add(one[oneIndex + 1 + index])
                    }
                }
                return
            }
            // ensuring all common binding values are equal in value
            (bindingsOne and bindingsTwo).bitIterator().forEach { bit ->
                val oneOffset = (((1 shl bit) - 1) and bindingsOne).countOneBits()
                val twoOffset = (((1 shl bit) - 1) and bindingsTwo).countOneBits()
                if (one[oneIndex + 1 + oneOffset] != two[twoIndex + 1 + twoOffset]) {
                    // incompatible mappings
                    return
                }
            }

            // knowing that both mappings are compatible, we can put the resulting binding mask in first
            target.insert { buf ->
                buf.add(bindingsOne or bindingsTwo)
                // all of their mappings can now be added
                val a = bindingsOne.bitIterator()
                val b = bindingsTwo.bitIterator()
                var left = a.next()
                var right = b.next()
                while (true) {
                    when {
                        left < right -> {
                            buf.add(one[oneIndex /* + 1 */ + oneSize - a.remaining() /* - 1 */])
                            left = if (a.hasNext()) {
                                a.next()
                            } else {
                                // all other elements to the right can get added right away
                                val remaining = b.remaining() + 1
                                repeat(remaining) {
                                    buf.add(two[twoIndex + 1 + twoSize - remaining + it])
                                }
                                break
                            }
                        }
                        right < left -> {
                            buf.add(two[twoIndex /* + 1 */ + twoSize - b.remaining() /* - 1 */])
                            right = if (b.hasNext()) {
                                b.next()
                            } else {
                                // all other elements to the right can get added right away
                                val remaining = a.remaining() + 1
                                repeat(remaining) {
                                    buf.add(one[oneIndex + 1 + oneSize - remaining + it])
                                }
                                break
                            }
                        }
                        else /* right == left */ -> {
                            // no equality check required as the compatibility check already passed
                            buf.add(one[oneIndex /* + 1 */ + oneSize - a.remaining() /* - 1 */])
                            left = if (a.hasNext()) {
                                a.next()
                            } else {
                                // all other elements to the right can get added right away
                                // this first step, `terms[i++] = other.get(right)`, is not required, as here, left == right
                                val remaining = b.remaining()
                                repeat(remaining) {
                                    buf.add(two[twoIndex + 1 + twoSize - remaining + it])
                                }
                                break
                            }
                            right = if (b.hasNext()) {
                                b.next()
                            } else {
                                // all other elements to the right can get added right away
                                val remaining = a.remaining() + 1
                                repeat(remaining) {
                                    buf.add(one[oneIndex + 1 + oneSize - remaining + it])
                                }
                                break
                            }
                        }
                    }
                }
            }

        }
    }
}
