package dev.tesserakt

import dev.tesserakt.sparql.runtime.collection.integer.IntCollectionView
import dev.tesserakt.sparql.runtime.collection.integer.IntHashMap
import dev.tesserakt.sparql.runtime.collection.integer.viewOf
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State
import kotlin.random.Random

private const val TEST_SIZE = 1000

@State(Scope.Benchmark)
class IntMapBenchmark {

    private lateinit var insertions: List<Pair<IntCollectionView, Int>>
    private lateinit var deletions: List<Pair<IntCollectionView, Int>>

    @Setup
    fun setup() {
        val random = Random(0)
        val data = buildList {
            repeat(TEST_SIZE) {
                add(viewOf(random.nextInt(), random.nextInt()) to random.nextInt())
            }
        }
        // insertions and deletions are both a shuffled variant of the generated data pool
        insertions = data.shuffled(random)
        deletions = data.shuffled(random)
    }

    @Benchmark
    fun linkedHashMap(): Int {
        val map = linkedMapOf<IntCollectionView, Int>()
        return eval(map)
    }

    @Benchmark
    fun regularHashMap(): Int {
        val map = hashMapOf<IntCollectionView, Int>()
        return eval(map)
    }

//    @Benchmark
    fun smallIntMap(): Int {
        val map = IntHashMap(2, TEST_SIZE)
        return eval(map)
    }

//    @Benchmark
    fun mediumIntMap(): Int {
        val map = IntHashMap(2, TEST_SIZE * 2)
        return eval(map)
    }

    @Benchmark
    fun bigIntMap(): Int {
        val map = IntHashMap(2, TEST_SIZE * 3)
        return eval(map)
    }

    private fun eval(map: MutableMap<IntCollectionView, Int>): Int {
        insertions.forEach { (key, value) -> map[key] = value }
        // also getting the elements in insertion and deletion order 100 times
        var sum = 0
        repeat(100) {
            insertions.forEach { (key) -> sum += map[key]!! }
            deletions.forEach { (key) -> sum += map[key]!! }
        }
        // and deleting them again
        deletions.forEach { (key) -> map.remove(key) }
        return sum
    }

    private fun eval(map: IntHashMap): Int {
        insertions.forEach { (key, value) -> map[key] = value }
        // also getting the elements in insertion and deletion order
        var sum = 0
        repeat(100) {
            insertions.forEach { (key) -> sum += map[key] }
            deletions.forEach { (key) -> sum += map[key] }
        }
        // and deleting them again
        deletions.forEach { (key) -> map.remove(key) }
        return sum
    }

}
