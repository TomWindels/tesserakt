
import dev.tesserakt.sparql.runtime.collection.VariableWidthBitsetMappingCollection
import dev.tesserakt.sparql.runtime.evaluation.mapping.BitsetMapping
import kotlin.test.*

class VariableWidthBitsetMappingCollectionTest {

    @Test
    fun insertion() {
        val collection = VariableWidthBitsetMappingCollection()
        collection.forEach { _ ->
            fail("No elements expected yet!")
        }
        val testMapping = BitsetMapping(1, intArrayOf(0))
        collection.add(testMapping)
        assertEquals(1, collection.size)
        val iter = collection.iterator()
        assertEquals(testMapping, iter.next())
        assertFalse(iter.hasNext())
        assertFails { iter.next() }
    }

}
