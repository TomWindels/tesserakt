
import dev.tesserakt.sparql.endpoint.server.impl.LRUCache
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class LRUTest {

    @Test
    fun small() {
        val cache = LRUCache<Int, String>(3)
        assert(cache.isEmpty())
        cache[0] = "0"
        assert(cache.isNotEmpty())
        assertEquals("0", cache[0])
        cache.forEach { (key, value) ->
            assertEquals(0, key)
            assertEquals("0", value)
        }
    }

    @Test
    fun eviction() {
        val cache = LRUCache<Int, String>(3)
        repeat(10) {
            cache[it] = it.toString()
        }
        assertContains(cache, 7)
        assertContains(cache, 8)
        assertContains(cache, 9)
        repeat(7) {
            assert(it !in cache)
        }
        assertEquals("7", cache[7])
        assertEquals("8", cache[8])
        assertEquals("9", cache[9])
    }

    @Test
    fun outOfOrderAccess() {
        val cache = LRUCache<Int, String>(3)
        cache[11] = "11"
        repeat(10) {
            // making sure it's recently accessed
            val access = cache[11]
            cache[it] = it.toString()
        }
        assertContains(cache, 11)
        assertContains(cache, 8)
        assertContains(cache, 9)
        repeat(7) {
            assert(it !in cache)
        }
        assertEquals("11", cache[11])
        assertEquals("8", cache[8])
        assertEquals("9", cache[9])
    }

    @Test
    fun evictionCallback() {
        val evicted = mutableSetOf<Int>()
        val cache = LRUCache<Int, String>(
            capacity = 3,
            onEviction = { key, _ -> evicted.add(key) }
        )
        repeat(10) {
            cache[it] = it.toString()
        }
        repeat(7) {
            assertContains(evicted, it)
        }
    }

}