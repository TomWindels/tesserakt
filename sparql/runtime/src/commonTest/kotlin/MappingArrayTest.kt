
import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.sparql.runtime.collection.CompleteHashMappingArray
import dev.tesserakt.sparql.runtime.collection.MappingArray
import dev.tesserakt.sparql.runtime.collection.MultiHashMappingArray
import dev.tesserakt.sparql.runtime.collection.SimpleMappingArray
import dev.tesserakt.sparql.runtime.evaluation.BindingIdentifierSet
import dev.tesserakt.sparql.runtime.evaluation.context.GlobalQueryContext
import dev.tesserakt.sparql.runtime.evaluation.mapping.mappingOf
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class MappingArrayTest {

    val context = GlobalQueryContext
    val bindings = run {
        val rng = Random(0)
        buildSet {
            repeat(10) {
                add(rng.nextString())
            }
        }.toList()
    }

    val mappings = run {
        buildList {
            val rng = Random(0)
            val stringPool = buildList {
                repeat(30) {
                    add(Quad.NamedTerm(rng.nextString()))
                }
            }
            repeat(1000) {
                add(mappingOf(context, *bindings.map { it to stringPool.random(rng) }.toTypedArray()))
            }
        }
    }

    @Test
    fun simpleArrayInsertion() {
        check(SimpleMappingArray())
    }

    @Test
    fun multiHashArrayInsertion() {
        check(MultiHashMappingArray(BindingIdentifierSet(intArrayOf(0, 1))))
    }

    @Test
    fun completeHashArrayInsertion() {
        check(CompleteHashMappingArray(BindingIdentifierSet(intArrayOf(0, 1))))
    }

    fun check(array: MappingArray) {
        mappings.forEachIndexed { index, mapping ->
            array.add(mapping)
            assertEquals(index + 1, array.cardinality.toInt())
            assertNotNull(array.iter(mapping).find { it == mapping }, "Failed to find mapping $mapping in $array, index $index")
        }
        println("Intermediate state: $array")
        var i = 0
        array.iter().forEach { ++i }
        assertEquals(mappings.size, i)
        mappings.forEachIndexed { index, mapping ->
            assertNotNull(array.iter(mapping).find { it == mapping }, "Failed to find mapping $mapping in $array, removal index $index")
            array.remove(mapping)
            assertEquals(mappings.size - index - 1, array.cardinality.toInt())
        }
    }

    private fun Random.nextString(length: Int = nextInt().coerceIn(10, 50)): String {
        return ByteArray(length) { (nextInt() % 12 + 109).toByte() }.decodeToString()
    }

}
