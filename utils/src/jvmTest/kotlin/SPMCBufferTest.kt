
import dev.tesserakt.concurrent.ConcurrentSet
import dev.tesserakt.concurrent.SPMCBuffer
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals

class SPMCBufferTest {

    @Test
    fun simple() {
        val iter = (1 .. 10_000_000).iterator()
        var i = 0
        val buf = SPMCBuffer<Int>()
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

    @Test
    fun concurrentReaders() {
        val buf = SPMCBuffer<Int>()
        val received = ConcurrentSet<Int>(11_000_000)
        val readers = List(4) {
            thread {
                var element = buf.poll()
                while (element != null) {
                    received.add(element)
                    element = buf.poll()
                }
            }
        }
        // we are the writer thread
        repeat(10_000_000) { element ->
            buf.push(element)
        }
        // the writer 'thread' finished, so we can mark the buf done as well
        buf.close()
        readers.forEach { it.join() }
        assertEquals(10_000_000, received.size)
    }

}
