import dev.tesserakt.rdf.types.Quad.Companion.asLiteralTerm
import dev.tesserakt.rdf.types.Quad.Companion.asNamedTerm
import dev.tesserakt.sparql.runtime.collection.VariableWidthBitsetMappingCollection
import dev.tesserakt.sparql.runtime.evaluation.context.GlobalQueryContext
import dev.tesserakt.sparql.runtime.evaluation.mapping.BitsetMapping
import dev.tesserakt.sparql.runtime.evaluation.mapping.Mapping
import dev.tesserakt.sparql.runtime.stream.BitsetMappingStream
import dev.tesserakt.sparql.runtime.stream.collect
import dev.tesserakt.sparql.runtime.stream.join
import dev.tesserakt.sparql.runtime.stream.toStream
import dev.tesserakt.sparql.util.Counter
import junit.framework.TestCase.assertFalse
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.fail

class BitsetMappingStreamTest {

    @Test
    fun cartesianJoin() {
        val data1 = VariableWidthBitsetMappingCollection().apply {
            add(BitsetMapping(1, intArrayOf(0)))
            add(BitsetMapping(1, intArrayOf(1)))
            add(BitsetMapping(1, intArrayOf(2)))
        }
        val data2 = VariableWidthBitsetMappingCollection().apply {
            add(BitsetMapping(2, intArrayOf(0)))
            add(BitsetMapping(2, intArrayOf(1)))
            add(BitsetMapping(2, intArrayOf(2)))
        }
        val stream1 = BitsetMappingStream(data1)
        val stream2 = BitsetMappingStream(data2)
        val results = BitsetMappingStream.join(stream1, stream2)
        val iter = results.iterator()
        repeat(3) { i ->
            repeat(3) { j ->
                assert(iter.hasNext())
                assertEquals(BitsetMapping(3, intArrayOf(i, j)),  iter.next())
            }
        }
        assertFalse(iter.hasNext())
        assertFails { iter.next() }
    }

    @Test
    fun incompatibleJoin() {
        val data1 = VariableWidthBitsetMappingCollection().apply {
            add(BitsetMapping(1, intArrayOf(0)))
            add(BitsetMapping(1, intArrayOf(1)))
            add(BitsetMapping(1, intArrayOf(2)))
        }
        val data2 = VariableWidthBitsetMappingCollection().apply {
            add(BitsetMapping(1, intArrayOf(3)))
            add(BitsetMapping(1, intArrayOf(4)))
            add(BitsetMapping(1, intArrayOf(5)))
        }
        val stream1 = BitsetMappingStream(data1)
        val stream2 = BitsetMappingStream(data2)
        val results = BitsetMappingStream.join(stream1, stream2)
        val iter = results.iterator()
        assertFalse(iter.hasNext())
        assertFails { iter.next() }
    }

    @Test
    fun longIncompatibleJoin() {
        val data1 = VariableWidthBitsetMappingCollection().apply {
            add(BitsetMapping(0b11_111, intArrayOf(0, 1, 2, 3, 4)))
        }
        val data2 = VariableWidthBitsetMappingCollection().apply {
            add(BitsetMapping(0b11_111, intArrayOf(0, 1, 2, 3, 5)))
        }
        val stream1 = BitsetMappingStream(data1)
        val stream2 = BitsetMappingStream(data2)
        val results = BitsetMappingStream.join(stream1, stream2)
        val iter = results.iterator()
        assertFalse(iter.hasNext())
        assertFails { iter.next() }
    }

    @Test
    fun regularJoin() {
        val data1 = VariableWidthBitsetMappingCollection().apply {
            add(BitsetMapping(5, intArrayOf(0, 1)))
        }
        val data2 = VariableWidthBitsetMappingCollection().apply {
            add(BitsetMapping(3, intArrayOf(0, 2)))
        }
        val stream1 = BitsetMappingStream(data1)
        val stream2 = BitsetMappingStream(data2)
        val results = BitsetMappingStream.join(stream1, stream2)
        val iter = results.iterator()
        assert(iter.hasNext())
        assertEquals(BitsetMapping(7, intArrayOf(0, 2, 1)), iter.next())
        assertFalse(iter.hasNext())
        assertFails { iter.next() }
    }

    @Test
    fun mixedJoin() {
        val size = 7_500
        val variance = 50
        val bindings = listOf(
            "person" to List(variance) { "http://example/person_${it}".asNamedTerm() },
            "job" to List(variance) { "http://example/job_${it}".asNamedTerm() },
            "name" to List(variance) { "http://example/name_${it}".asNamedTerm() },
            "age" to List(variance) { it.asLiteralTerm() },
        )
        fun createMapping(id: Int): BitsetMapping {
            val rng = Random(id)
            return BitsetMapping(
                context = GlobalQueryContext,
                source = bindings.associate { it.first to it.second.random(rng) }.filter { rng.nextBoolean() }
            )
        }

        val left = mutableListOf<BitsetMapping>()
        val right = mutableListOf<BitsetMapping>()
        val random = Random(1)
        repeat(size) {
            val new = createMapping(random.nextInt())
            if (random.nextBoolean()) {
                left.add(new)
            } else {
                right.add(new)
            }
        }

        // reference join count
        val expected = left
            .toStream<Mapping>()
            .join(right.toStream())
            .collect()
            .toList()
            .let { Counter(it) }
        val result = BitsetMappingStream.join(
            one = BitsetMappingStream(VariableWidthBitsetMappingCollection(left)),
            two = BitsetMappingStream(VariableWidthBitsetMappingCollection(right)),
        )
        result.forEachIndexed { i, mapping ->
            if (mapping in expected) {
                expected.decrement(mapping)
            } else {
                fail("Did not expect $mapping in the expected set (result #$i)")
            }
        }
        if (expected.any { it.value != 0 }) {
            fail("Non-zero mappings remaining: ${expected.filter { it.value != 0 }.joinToString { it.key.toString() } }")
        }
    }

}
