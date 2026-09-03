import dev.tesserakt.concurrent.SimpleConcurrentList
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SimpleConcurrentListTest {

    @Test
    fun simple() {
        val arr = SimpleConcurrentList<Int>()
        repeat(100) {
            assertEquals(it, arr.add(it))
        }
        repeat(100) {
            assertEquals(it, arr[it])
        }
    }

    @Test
    fun concurrent() {
        val count = 20_000

        val arr = SimpleConcurrentList<Int>()
        List(3) { threadId ->
            thread {
                repeat(count) { i ->
                    val index = arr.add(i + threadId * count)
                    assert(index < 3 * count)
                }
            }
        }.forEach {
            it.join()
        }
        assertEquals(3 * count, arr.size)
        assertTrue {
            arr._buf.size == 3 * count || arr._buf[3 * count] === SimpleConcurrentList.Unused
        }
        // dropping the 'Unused' marker instance if it is present
        val encountered: Set<Int> = arr._buf.filterIsInstanceTo<Int, _>(mutableSetOf())
        assertEquals(3 * count, encountered.size)
        for (i in 0 ..< 3 * count) {
            assertContains(encountered, i)
        }
    }

}
