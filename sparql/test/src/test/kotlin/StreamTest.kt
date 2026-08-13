
import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.sparql.runtime.evaluation.context.GlobalQueryContext
import dev.tesserakt.sparql.runtime.evaluation.mapping.hashable
import dev.tesserakt.sparql.runtime.evaluation.mapping.mappingOf
import dev.tesserakt.sparql.runtime.stream.*
import dev.tesserakt.sparql.util.Counter
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
        val filtered1 = input.toStream().mappedNonNull { it.takeIf { it % 2 == 1 } }
        assertTrue { filtered1.all { it % 2 == 1 } }
        assertEquals(filtered1.cardinality.toInt(), input.size)
        val filtered2 = input.toStream().filtered { it % 2 == 1 }
        assertTrue { filtered2.all { it % 2 == 1 } }
        assertEquals(filtered2.cardinality.toInt(), input.size)
        assertContentEquals(filtered1, filtered2)
    }

    @Test
    fun joining() {
        val a = (0..10).map { mappingOf(GlobalQueryContext, "value" to Quad.Literal(it)) }
            .toStream()
        val b = (7..10).map { mappingOf(GlobalQueryContext, "value" to Quad.Literal(it)) }
            .toStream()
        val joined1 = a.join(b)
        val joined2 = a.product(b).mappedNonNull { (a, b) -> a.join(b) }
        val check = Counter(joined1.map { it.hashable() })
        assertEquals(joined1.cardinality, joined2.cardinality)
        assertTrue { check.current.size == 4 }
        assertTrue { check.all { it.value == 1 } }
        joined2.forEach { check.decrement(it.hashable()) }
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

}
