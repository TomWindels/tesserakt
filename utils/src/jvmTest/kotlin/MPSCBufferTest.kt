
import dev.tesserakt.concurrent.MPSCBuffer
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MPSCBufferTest {

    @Test
    fun simple() {
        val iter = (1 .. 10_000_000).iterator()
        var i = 0
        val buf = MPSCBuffer<Int>()
        buf.use {
            iter.forEach { element ->
                buf.push(element)
                val ele = buf.poll()
                ++i
                assertEquals(element, ele)
            }
        }
        assertEquals(10_000_000, i)
    }

    // also a single threaded test, but we constantly push until the buffer is at max capacity (16)
    @Test
    fun highPressure() {
        val buf = MPSCBuffer<Int>()
        val received = mutableSetOf<Int>()
        buf.use {
            repeat(16) { ele ->
                buf.push(ele)
            }
            repeat(16) {
                val ele = buf.poll()
                assertTrue { ele in (0 ..< 16) }
                received.add(ele!!)
            }
        }
        assertEquals(null, buf.poll())
        assertEquals(16, received.size)
    }

    @Test
    fun concurrentWriters() {
        val buf = MPSCBuffer<Int>()
        // single reader thread, active first as otherwise the writers would be blocked
        // as we're combining all multithreaded results into a single thread result holder, we can
        //  put it in a thread-unsafe structure
        val received = HashSet<Int>(2_000_000)
        val reader = thread {
            var element = buf.poll()
            while (element != null) {
                assert(received.add(element))
                element = buf.poll()
            }
        }
        List(4) { i ->
            thread {
                val iter = ((i * 250_000) ..< (i + 1) * 250_000).iterator()
                try {
                    iter.forEach { element ->
                        buf.push(element)
                    }
                } catch (t: Throwable) {
                    println(t)
                }
            }
        }.forEach { it.join() }
        // the writer threads finished, so we can mark the buf done as well
        buf.close()
        // which then allows the reader to finish too
        reader.join()
        assertEquals(1_000_000, received.size)
    }

}
