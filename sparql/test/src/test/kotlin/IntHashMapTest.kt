
import dev.tesserakt.sparql.runtime.collection.integer.IntCollectionView
import dev.tesserakt.sparql.runtime.collection.integer.IntHashMap
import dev.tesserakt.sparql.runtime.collection.integer.viewOf
import junit.framework.TestCase.assertEquals
import kotlin.math.absoluteValue
import kotlin.random.Random
import kotlin.test.Test

class IntHashMapTest {

    @Test
    fun smallMap() {
        val map = IntHashMap(2, 4)
        val key = viewOf(0, 1)
        assert(map.set(key, 1))
        println("Map = $map")
        println("Details\n${map.debugString()}")
    }

    @Test
    fun fillingUpMap() {
        val map = IntHashMap(2, 90)
        repeat(90) { i ->
            val key = viewOf(0, i)
            assert(map.set(key, i * i))
        }
        repeat(90) { i ->
            val key = viewOf(0, i)
            assertEquals(i * i, map[key])
        }
    }

    @Test
    fun clearingOutMap() {
        val map = IntHashMap(2, 90)
        repeat(90) { i ->
            val key = viewOf(0, i)
            assert(map.set(key, i * i))
        }
        repeat(90) { i ->
            val key = viewOf(0, i)
            assertEquals(i * i, map[key])
            val removed = map.remove(key)
            assertEquals(i * i, removed)
            assertEquals(IntHashMap.NOT_FOUND, map[key])
        }
    }

    @Test
    fun mixedOperations() {
        val TEST_SIZE = 1000

        val map = IntHashMap(2, 2 * TEST_SIZE)
        val reference = mutableMapOf<IntCollectionView, Int>()
        val inserted = mutableListOf<Pair<IntCollectionView, Int>>()
        val missing = mutableListOf<Pair<IntCollectionView, Int>>()

        fun insert(index: Int) {
            val element = missing.removeAt(index)
            inserted.add(element)

            map[element.first] = element.second
            reference[element.first] = element.second
        }

        fun remove(index: Int) {
            val element = inserted.removeAt(index)
            missing.add(element)

            map.remove(element.first)
            reference.remove(element.first)
        }

        fun check() {
            val transformed = map.toMap()
            assert(reference == transformed) {
                val missing = reference - transformed.keys
                val superfluous = transformed - reference.keys
                "Value mismatch!\n Expected:\n$reference\n Received:\n$transformed\n Missing:\n$missing\n Superfluous:\n $superfluous\n\n Debug view:\n${map.debugString()}"
            }
        }

        val random = Random(0)
        repeat(TEST_SIZE) {
            missing.add(viewOf(random.nextInt(), random.nextInt()) to random.nextInt())
        }

        check()
        repeat(TEST_SIZE * 10) {
            val next = random.nextInt().absoluteValue % TEST_SIZE
            if (next < inserted.size) {
                remove(next)
            } else {
                insert(next - inserted.size)
            }
            check()
        }
    }

}
