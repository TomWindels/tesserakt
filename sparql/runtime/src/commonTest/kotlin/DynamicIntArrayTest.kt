
import dev.tesserakt.sparql.runtime.collection.integer.DynamicIntArray
import kotlin.test.*

class DynamicIntArrayTest {

    @Test
    fun simple() {
        val arr = DynamicIntArray()
        assertEquals(0, arr.size)
        assertEquals(0, arr.capacity)
        assertEquals(0, arr.free)
        println(arr)
        arr.add(1)
        println(arr)
        assertEquals(1, arr.size)
        assertContains(arr, 1)
        assertEquals(1024, arr.capacity)
        assertEquals(1023, arr.free)
        arr.pop()
        println(arr)
        assertEquals(0, arr.size)
        assertEquals(1024, arr.capacity)
        assertEquals(1024, arr.free)
    }

    @Test
    fun large() {
        val arr = DynamicIntArray()
        println(arr)
        assertEquals(0, arr.size)
        assertEquals(0, arr.capacity)
        assertEquals(0, arr.free)
        arr.addAll((0..1024).toList())
        println(arr)
        assertEquals(1025, arr.size)
        assertTrue { 0 in arr }
        assertTrue { 1 in arr }
        assertTrue { 2 in arr }
        assertEquals(2048, arr.capacity)
        assertEquals(1023, arr.free)
    }

    @Test
    fun removal() {
        val arr = DynamicIntArray()
        // adding 0 .. 4 through an int array this time
        arr.addAll(IntArray(5) { it })
        println(arr)
        assertEquals(5, arr.size)
        assertEquals(1024, arr.capacity)
        assertEquals(1019, arr.free)
        // removing 0 & 1
        arr.swapRemoveRange(0, 2)
        assertEquals(3, arr.size)
        assertEquals(1024, arr.capacity)
        assertEquals(1021, arr.free)
        // 'swap' remove, we maintain order of the last two elements, which have now been positioned at the front
        assertContentEquals(listOf(3, 4, 2), arr)
    }

}
