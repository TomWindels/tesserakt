import dev.tesserakt.sparql.util.Counter
import kotlin.test.*

class CounterTest {

    @Test
    fun counter1() {
        val counter = Counter<String>()
        assertTrue { counter.flatten().isEmpty() }
        assertContentEquals(listOf(), counter.flatten())
        counter.increment("test")
        assertFalse { counter.flatten().isEmpty() }
        assertEquals(1, counter.flatten().size)
        assertContentEquals(listOf("test"), counter.flatten())
        counter.decrement("test")
        assertTrue { counter.flatten().isEmpty() }
        assertEquals(0, counter.flatten().size)
        assertContentEquals(listOf(), counter.flatten())
        assertFails {
            counter.decrement("test")
        }
    }

    @Test
    fun counter2() {
        val counter = Counter<String>()
        assertContentEquals(listOf(), counter.flatten())
        counter.increment("test1")
        assertEquals(1, counter.flatten().size)
        assertContentEquals(listOf("test1"), counter.flatten())
        counter.increment("test2")
        assertEquals(2, counter.flatten().size)
        assertContentEquals(listOf("test1", "test2").sorted(), counter.flatten().sorted())
        counter.decrement("test1")
        assertEquals(1, counter.flatten().size)
        assertContentEquals(listOf("test2"), counter.flatten())
        assertFails {
            counter.decrement("test")
        }
    }

    @Test
    fun counter3() {
        val counter = Counter<String>()
        assertTrue { counter.flatten().isEmpty() }
        assertContentEquals(listOf(), counter.flatten())
        repeat(10) { iteration ->
            assertEquals(iteration, counter.flatten().size)
            assertContentEquals(List(iteration) { "test" }, counter.flatten())
            counter.increment("test")
            assertEquals(iteration + 1, counter.flatten().size)
            assertContentEquals(List(iteration + 1) { "test" }, counter.flatten())
            assertFalse { counter.flatten().isEmpty() }
        }
        counter.clear("test")
        assertEquals(0, counter.flatten().size)
        assertContentEquals(listOf(), counter.flatten())
        assertFails {
            counter.decrement("test")
        }
    }

}
