import dev.tesserakt.sparql.util.Counter
import kotlin.test.*

class CounterTest {

    @Test
    fun counter1() {
        val counter = Counter<String>()
        assertTrue { counter.flattened.isEmpty() }
        assertContentEquals(listOf(), counter.flattened)
        counter.increment("test")
        assertFalse { counter.flattened.isEmpty() }
        assertEquals(1, counter.flattened.size)
        assertContentEquals(listOf("test"), counter.flattened)
        counter.decrement("test")
        assertTrue { counter.flattened.isEmpty() }
        assertEquals(0, counter.flattened.size)
        assertContentEquals(listOf(), counter.flattened)
        assertFails {
            counter.decrement("test")
        }
    }

    @Test
    fun counter2() {
        val counter = Counter<String>()
        assertContentEquals(listOf(), counter.flattened)
        counter.increment("test1")
        assertEquals(1, counter.flattened.size)
        assertContentEquals(listOf("test1"), counter.flattened)
        counter.increment("test2")
        assertEquals(2, counter.flattened.size)
        assertContentEquals(listOf("test1", "test2").sorted(), counter.flattened.sorted())
        counter.decrement("test1")
        assertEquals(1, counter.flattened.size)
        assertContentEquals(listOf("test2"), counter.flattened)
        assertFails {
            counter.decrement("test")
        }
    }

    @Test
    fun counter3() {
        val counter = Counter<String>()
        assertTrue { counter.flattened.isEmpty() }
        assertContentEquals(listOf(), counter.flattened)
        repeat(10) { iteration ->
            assertEquals(iteration, counter.flattened.size)
            assertContentEquals(List(iteration) { "test" }, counter.flattened)
            counter.increment("test")
            assertEquals(iteration + 1, counter.flattened.size)
            assertContentEquals(List(iteration + 1) { "test" }, counter.flattened)
            assertFalse { counter.flattened.isEmpty() }
        }
        counter.clear("test")
        assertEquals(0, counter.flattened.size)
        assertContentEquals(listOf(), counter.flattened)
        assertFails {
            counter.decrement("test")
        }
    }

}
