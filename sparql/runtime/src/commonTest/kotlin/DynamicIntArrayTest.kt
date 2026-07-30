import dev.tesserakt.sparql.runtime.collection.integer.DynamicIntArray
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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

}
