import dev.tesserakt.sparql.runtime.collection.GrowingIntArray
import junit.framework.TestCase.assertTrue
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.fail

class GrowingIntArrayTest {

    @Test
    fun insertion() {
        val arr = GrowingIntArray()
        repeat(1000) { i ->
            arr.add(i)
            (0..<i).forEach {
                assertContains(arr, it)
            }
        }
    }

    @Test
    fun deletion() {
        val arr = GrowingIntArray()
        repeat(1000) { i ->
            arr.add(i)
            assertTrue((0..<i).all { it in arr })
        }
        // removing the last 300 elements
        val removed = mutableSetOf<Int>()
        repeat(300) { i ->
            val next = arr
                .indexOf(700 + i)
                .takeIf { it != -1 }
                ?: fail("Failed to find #${700 + i}")

            try {
                arr.removeRange(next, 1)
            } catch (e: Exception) {
                fail("Failed to remove #${700 + i} at index $next for $arr", e)
            }
            removed.add(700 + i)
            (0..<1000).forEach { nr ->
                val deleted = nr in removed
                assert(nr in arr && !deleted || nr !in arr && deleted) {
                    "$nr is supposed to be ${if (deleted) "removed" else "present"} (iteration i=$i)"
                }
            }
        }
    }

}
