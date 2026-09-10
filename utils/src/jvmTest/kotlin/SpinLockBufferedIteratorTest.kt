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
        ThreadedTaskRunner.SpinLoopBufferedIterator(iter).use { buffered ->
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
        ThreadedTaskRunner.SpinLoopBufferedIterator(iter).use { buffered ->
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

    @Test
    fun concurrentReadFailure() {
        val iter = object: Iterator<Int> {

            private var i = 0

            override fun hasNext(): Boolean {
                return i < 1_000_000
            }

            override fun next(): Int {
                if (i == 3000) {
                    // oh no!
                    throw RuntimeException("Simulated iterator failure!")
                }
                return ++i
            }

        }
        val results = ConcurrentHashMap.newKeySet<Int>()
        val failures = Array<Throwable?>(2) { null }
        ThreadedTaskRunner.SpinLoopBufferedIterator(iter).use { buffered ->
            val t = thread { buffered.producerLoop() }
            val readers = List(2) {
                thread {
                    var element = buffered.getNext()
                    while (element != null) {
                        results.add(element)
                        element = try {
                            buffered.getNext()
                        } catch (t: Throwable) {
                            failures[it] = t
                            // no need to clog the console with thread failures
                            return@thread
                        }
                    }
                    throw AssertionError("Reader thread reached its natural end unexpectedly!")
                }
            }
            readers.forEach { it.join() }
            t.join()
            if (failures.any { it == null }) {
                fail("Reader #${failures.indexOfFirst { it == null } + 1} failed to fail!")
            }
        }
    }

}
