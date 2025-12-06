
import dev.tesserakt.sparql.util.SortedCounter
import dev.tesserakt.util.map
import junit.framework.TestCase.assertEquals
import java.util.*
import kotlin.math.absoluteValue
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

class SortedCounterTest {

    @Test
    fun insertion() {
        val counter = SortedCounter<Int>()
        val ref = TreeMap<Int, Int>()
        val rng = Random(0)
        repeat(100) {
            val nr = rng.nextInt().absoluteValue % 10
            counter.increment(nr)
            ref.compute(nr) { _, oldValue -> (oldValue ?: 0) + 1 }
            check(ref, counter)
        }
    }

    @Test
    fun reversing() {
        val counter = SortedCounter<Int>()
        val rng = Random(0)
        repeat(100) {
            val nr = rng.nextInt().absoluteValue % 10
            counter.increment(nr)
        }

        assertTrue { counter.current.isNotEmpty() }
        assertEquals(100, counter.flattened.size)

        counter
            .reversed()
            .map { it.key }
            .zipWithNext()
            .forEach { (prev, curr) -> assert(prev > curr) }
    }

    @Test
    fun deletion() {
        val counter = SortedCounter<Int>()
        val ref = TreeMap<Int, Int>()
        val rng = Random(0)
        repeat(100) {
            val nr = rng.nextInt().absoluteValue % 10
            counter.increment(nr)
            ref.compute(nr) { _, oldValue -> (oldValue ?: 0) + 1 }
            check(ref, counter)
        }

        while (ref.isNotEmpty()) {
            val nr = ref.keys.random(rng)
            counter.decrement(nr)
            ref.compute(nr) { _, oldValue -> ((oldValue ?: 0) - 1).takeIf { it != 0 } }
            check(ref, counter)
        }
    }

    @Test
    fun clashingOrder() {
        // using a custom order in which even numbers clash with the next uneven value, making it possible for orders
        //  such as 0, 1, 3, 2, ... to occur, as 0 & 1 map to the same value, as do 2 & 3, etc.
        val counter = SortedCounter<Int>(comparator = Comparator { a, b -> a / 2 - b / 2 })

        val rng = Random(0)
        val inserted = mutableSetOf<Int>()
        repeat(100) {
            val nr = rng.nextInt().absoluteValue % 10
            inserted.add(nr)

            counter.increment(nr)
            counter
                // the <= check applies to their halved values
                .map { it.key / 2 }
                .zipWithNext()
                .forEach { (one, two) ->
                    assert(one <= two) { "Ordering check failed for $one - $two" }
                }
            // even though the orders are clashing, it's still expected to contain all keys ever inserted
            inserted.forEach { assert(it in counter) }

        }
        // and we should be able to decrement them all again too
        // limiting the number of decrements to the lowest number of elements available
        val count = counter.minOf { it.value }
        repeat(count) {
            val nr = rng.nextInt().absoluteValue % 10
            counter.decrement(nr)
        }
    }

    @Test
    fun unordered() {
        // using a custom order in which all entries clash
        val counter = SortedCounter<Int>(comparator = Comparator { _, _ -> 0 })

        val rng = Random(0)
        val inserted = mutableSetOf<Int>()
        repeat(100) {
            val nr = rng.nextInt().absoluteValue % 10
            inserted.add(nr)

            counter.increment(nr)
            // even though the orders are clashing, it's still expected to contain all keys ever inserted
            inserted.forEach { assert(it in counter) }
        }
    }

    @Test
    fun notFound() {
        val counter = SortedCounter<Int>()
        val rng = Random(0)
        repeat(100) {
            val nr = rng.nextInt().absoluteValue % 10
            assertFails {
                counter.decrement(nr)
            }
            assertEquals(0, counter.current.size)
            assertEquals(0, counter.flattened.size)
        }
    }

    private fun check(ref: TreeMap<Int, Int>, self: SortedCounter<Int>) {
        val flattened = ref.toList()
        val other = self.map { (key, value) -> key to value }
        assertContentEquals(ref.asIterable(), self.asIterable(), "$flattened\nvs\n$other")
    }

}
