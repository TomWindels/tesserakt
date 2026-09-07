import dev.tesserakt.concurrent.ThreadedTaskRunner
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class SpinLockBufferedIteratorTest {

    @Test
    fun simple() {
        val iter = (1 .. 1_000_000).iterator()
        val results = mutableSetOf<Int>()
        ThreadedTaskRunner.SpinLockBufferedIterator(iter).use { buffered ->
            val t = thread { buffered.producerLoop() }
            var element = buffered.getNext()
            while (element != null) {
                results.add(element)
                element = buffered.getNext()
            }
            assertEquals(1_000_000, results.size)
            t.join()
        }
    }

    @Test
    fun concurrentRead() {
        val iter = (1 .. 1_000_000).iterator()
        val results = ConcurrentHashMap.newKeySet<Int>()
        var failure: Throwable? = null
        ThreadedTaskRunner.SpinLockBufferedIterator(iter).use { buffered ->
            val t = thread { buffered.producerLoop() }
            val readers = List(2) {
                thread {
                    var element = buffered.getNext()
                    while (element != null) {
                        results.add(element)
                        element = try {
                            buffered.getNext()
                        } catch (t: Throwable) {
                            failure = t
                            throw t
                        }
                    }
                }
            }
            readers.forEach { it.join() }
            if (failure != null) {
                fail("A reader failed!", failure)
            }
            assertEquals(1_000_000, results.size, "First missing element: ${(1 .. 1_000_000).firstOrNull { it !in results }}")
            t.join()
        }
    }

}
