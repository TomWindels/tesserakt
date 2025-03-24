
import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.rdf.types.Quad.Companion.asLiteralTerm
import dev.tesserakt.rdf.types.Quad.Companion.asNamedTerm
import dev.tesserakt.sparql.runtime.evaluation.Mapping
import org.junit.Test
import kotlin.random.Random
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

private const val SIZE = 1000
private const val VARIANCE = 50

private typealias MapMapping = Map<String, Quad.Term>

class MappingTest {

    private val BINDINGS = listOf(
        "person" to List(VARIANCE) { "http://example/person_${it}".asNamedTerm() },
        "job" to List(VARIANCE) { "http://example/job_${it}".asNamedTerm() },
        "name" to List(VARIANCE) { "http://example/name_${it}".asNamedTerm() },
        "age" to List(VARIANCE) { it.asLiteralTerm() },
    )

    private fun createMapping(id: Int): MapMapping {
        val rng = Random(id)
        return BINDINGS.associate { it.first to it.second.random(rng) }.filter { rng.nextBoolean() }
    }

    private inline fun <K: Any, V: Any> Map<K, V>.compatibleWith(reference: Map<K, V>) =
        reference.all { (refKey, refValue) -> val data = this[refKey]; data == null || data == refValue}


    private fun join(a: MapMapping, b: MapMapping): MapMapping? {
        return if (a.compatibleWith(b)) {
            a + b
        } else {
            null
        }
    }

    @Test
    fun mappingConversion1() {
        val data = List(100) { createMapping(it) }
        val converted = data.map { Mapping(it) }
        assertContentEquals(data, converted.map { it.toMap() })
    }

    @Test
    fun mappingConversion2() {
        val data = List(100) { createMapping(it) }
        val converted = data.map { Mapping(it) }
        repeat(data.size) { index ->
            val map = data[index]
            map.forEach { (key, value) ->
                assertEquals(value, converted[index][key])
            }
        }
    }

    @Test
    fun mappingConversion3() {
        val data = List(100) { createMapping(it) }
        val converted = data.map { Mapping(it) }
        repeat(data.size) { index ->
            assertContentEquals(
                expected = data[index].map { it.toPair() }.sortedBy { it.first },
                actual = converted[index].asIterable().sortedBy { it.first })
        }
    }

    @Test
    fun mappingConversion4() {
        val data = List(100) { createMapping(it) }
        val converted = data.map { Mapping(it) }
        val rng = Random(1)
        val subset = data.map { it.toMutableMap().apply { keys.retainAll { rng.nextBoolean() } } }
        repeat(data.size) { index ->
            assertContentEquals(
                expected = subset[index].map { it.toPair() }.sortedBy { it.first },
                actual = converted[index].retain(subset[index].keys).asIterable().sortedBy { it.first })
        }
    }

    @Test
    fun joinMappings() {
        val random = Random(1)
        val left = mutableListOf<MapMapping>()
        val right = mutableListOf<MapMapping>()
        repeat(SIZE) {
            val new = createMapping(random.nextInt())
            if (random.nextBoolean()) {
                left.add(new)
            } else {
                right.add(new)
            }
        }
        val l = left.map { Mapping(it) }
        val r = right.map { Mapping(it) }
        val original = left.flatMap { l -> right.mapNotNull { r -> join(l, r) } }
        val new = l.flatMap { l -> r.mapNotNull { r -> l.join(r) } }
            .map { it.toMap() }
        assertContentEquals(original, new)
    }

}
