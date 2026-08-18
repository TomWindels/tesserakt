import dev.tesserakt.sparql.runtime.collection.SimpleFixedShapeMappingArray
import dev.tesserakt.sparql.runtime.evaluation.mapping.BitsetMapping
import dev.tesserakt.sparql.runtime.evaluation.mapping.Mapping
import dev.tesserakt.sparql.runtime.stream.*
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals

class JoinTest {

    @Test
    fun fullMatch() {
        validate(
            left = streamOf(
                BitsetMapping(
                    bindings = 3,
                    terms = IntArray(2)
                ),
                BitsetMapping(
                    bindings = 3,
                    terms = IntArray(2)
                ),
            ),
            right = streamOf(
                BitsetMapping(
                    bindings = 6,
                    terms = IntArray(2)
                ),
                BitsetMapping(
                    bindings = 6,
                    terms = IntArray(2)
                ),
            ),
        )
    }

    @Test
    fun partialMatch() {
        validate(
            left = streamOf(
                BitsetMapping(
                    bindings = 3,
                    terms = intArrayOf(0, 1)
                ),
                BitsetMapping(
                    bindings = 3,
                    terms = intArrayOf(2, 3)
                ),
            ),
            right = streamOf(
                BitsetMapping(
                    bindings = 6,
                    terms = intArrayOf(1, 0)
                ),
                BitsetMapping(
                    bindings = 6,
                    terms = intArrayOf(4, 2)
                ),
            ),
        )
    }

    @Test
    fun noMatch() {
        validate(
            left = streamOf(
                BitsetMapping(
                    bindings = 3,
                    terms = intArrayOf(0, 1)
                ),
                BitsetMapping(
                    bindings = 3,
                    terms = intArrayOf(2, 3)
                ),
            ),
            right = streamOf(
                BitsetMapping(
                    bindings = 6,
                    terms = intArrayOf(4, 5)
                ),
                BitsetMapping(
                    bindings = 6,
                    terms = intArrayOf(6, 7)
                ),
            ),
        )
    }

    @Test
    fun singleElement() {
        validate(
            left = streamOf(
                BitsetMapping(
                    bindings = 3,
                    terms = intArrayOf(0, 1)
                ),
                BitsetMapping(
                    bindings = 3,
                    terms = intArrayOf(2, 3)
                ),
            ),
            right = streamOf(
                BitsetMapping(
                    bindings = 6,
                    terms = intArrayOf(3, 5)
                ),
            ),
        )
    }

    @Test
    fun long() {
        val rng = Random(5)
        validate(
            left = List(500) {
                BitsetMapping(
                    bindings = 3,
                    terms = intArrayOf(
                        1,
                        // either one of the possible join values, or another random value
                        if (rng.nextBoolean()) {
                            3
                        } else if (rng.nextBoolean()) {
                            4
                        } else {
                            rng.nextInt(5, 13)
                        }
                    )
                )
            }.toStream(),
            right = streamOf(
                BitsetMapping(
                    bindings = 6,
                    terms = intArrayOf(3, 5)
                ),
                BitsetMapping(
                    bindings = 6,
                    terms = intArrayOf(4, 5)
                ),
            ),
        )
    }

    fun validate(
        left: Stream<Mapping>,
        right: Stream<Mapping>,
    ) {
        val fixedLeft = left.toFixedMappingStream()
        val fixedRight = right.toFixedMappingStream()

        assertContentEquals(
            expected = fixedLeft.collect().join(fixedRight),
            actual = fixedLeft.join(fixedRight)
        )
        println(fixedLeft.join(fixedRight).count())
    }

    fun validate(
        left: Stream<Mapping>,
        right: SingleStream<Mapping>,
    ) {
        val fixedLeft = left.toFixedMappingStream()

        assertContentEquals(
            expected = fixedLeft.collect().join(right),
            actual = fixedLeft.join(right)
        )
        println(fixedLeft.join(right).count())
    }

    private fun Stream<Mapping>.toFixedMappingStream(): FixedShapeMappingStream {
        return FixedShapeMappingArrayStream(
            backing = SimpleFixedShapeMappingArray(
                bindings = (iterator().next() as BitsetMapping).bindings
            ).apply { addAll(this@toFixedMappingStream) }
        )
    }

}
