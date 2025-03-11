
import dev.tesserakt.rdf.types.Quad.Companion.asLiteralTerm
import dev.tesserakt.sparql.types.runtime.evaluation.mappingOf
import dev.tesserakt.sparql.types.runtime.stream.*
import dev.tesserakt.sparql.types.util.Counter
import dev.tesserakt.util.compatibleWith
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StreamTest {

    @Test
    fun transform() {
        fun myTransform(left: Int, right: Int): Int {
            return (left + right) * 2
        }
        val a = streamOf(0)
        val b = streamOf(1)
        val transform = a.product(b).transform(maxCardinality = 1) { (a, b) -> streamOf(myTransform(a, b)) }
        val mapped = a.product(b).mapped { (a, b) -> myTransform(a, b) }
        assertContentEquals(transform, listOf(myTransform(0, 1)))
        assertContentEquals(transform, mapped)
    }

    @Test
    fun filtering() {
        val input = (0 until 10) + 13
        val filtered = input.toStream().mappedNonNull { it.takeIf { it % 2 == 0 } }
        assertTrue { filtered.all { it % 2 == 0 } }
        assertEquals(filtered.cardinality.toInt(), input.size)
    }

    @Test
    fun joining() {
        val a = (0..10).map { mappingOf("value" to it.asLiteralTerm()) }
            .toStream()
        val b = (7..10).map { mappingOf("value" to it.asLiteralTerm()) }
            .toStream()
        val joined1 = a.join(b)
        val joined2 = a.product(b).mappedNonNull { (a, b) -> if (a.compatibleWith(b)) a + b else null }
        val check = Counter(joined1)
        assertEquals(joined1.cardinality, joined2.cardinality)
        assertTrue { check.current.size == 4 }
        assertTrue { check.all { it.value == 1 } }
        joined2.forEach { check.decrement(it) }
        assertTrue("One: ${joined1.joinToString()}\nTwo: ${joined2.joinToString()}\nRemaining: $check") { check.current.isEmpty() }
    }

    @Test
    fun chaining() {
        val streams = (0 until 10).map { streamOf(it) }.toStream()
        val merged = streams.merge()
        val filtered = merged.mappedNonNull { it.takeIf { it % 2 == 0 } }
        assertEquals(streams.cardinality, merged.cardinality)
        assertEquals(merged.cardinality, filtered.cardinality)
        assertTrue { filtered.all { it % 2 == 0 } }
        assertContentEquals(
            expected = 0 until 10 step 2,
            actual = filtered,
            message = "Expected: ${0 until 10 step 2}\nReceived: ${filtered.joinToString()}\n"
        )
    }

    @Test
    fun removing() {
        // source = 0, 1, 2, 3, 4, 5, 6, 7, 8, 9
        val source = (0 until 10).toList().toStream()
        // target = 1, 3, 5, 7, 9
        val target = (1 until 10 step 2).toList()
        // result = 0, 2, 4, 6, 8
        val result = source.remove(target).collect()
        // collected, so the resulting cardinality is now smaller
        assertTrue { result.cardinality < source.cardinality }
        assertTrue { result.all { it % 2 == 0 } }
        assertContentEquals(0 until 10 step 2, result)
        assertEquals(result.size, 5)
    }

}
