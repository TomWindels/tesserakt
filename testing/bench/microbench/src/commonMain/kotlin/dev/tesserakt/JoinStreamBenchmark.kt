package dev.tesserakt

import dev.tesserakt.sparql.runtime.collection.GrowingIntArray
import dev.tesserakt.sparql.runtime.collection.VariableWidthBitsetMappingCollection
import dev.tesserakt.sparql.runtime.evaluation.context.GlobalQueryContext
import dev.tesserakt.sparql.runtime.evaluation.mapping.BitsetMapping
import dev.tesserakt.sparql.runtime.evaluation.mapping.Mapping
import dev.tesserakt.sparql.runtime.stream.*
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State
import kotlin.random.Random


private fun createMapping(id: Int): BitsetMapping {
    val rng = Random(id)
    return BitsetMapping(
        context = GlobalQueryContext,
        source = BINDINGS.associate { it.first to it.second.random(rng) }.filter { rng.nextBoolean() }
    )
}

/**
 * A collection of [BitsetMapping] instances, all having the same 'width', meaning that all elements have the same
 *  number of mapping elements inside. This fact makes it possible for more efficient mapping removal
 */
class FixedWidthBitsetMappingCollection(
    internal val buf: GrowingIntArray = GrowingIntArray(),
    // TODO: param for mapping size/width, asserted at every modification op
) {
    fun add(mapping: BitsetMapping) {
        buf.add(mapping.bindings)
        mapping.terms.iterator().forEach { buf.add(it) }
    }

    fun remove(mapping: BitsetMapping) {
        // jumping `mapping width` elements every time we do not find the one we're looking for
        val mappingWidth = mapping.count
        var i = 0
        while (i < buf.size) {
            if (
                buf[i] == mapping.bindings &&
                mapping.terms.allIndexed { j, value -> value == buf[i + 1 + j] }
            ) {
                buf.removeRange(i, mappingWidth)
                return
            }
            i += mappingWidth
        }
    }

    fun intIterator() = buf.iterator()

}

private inline fun IntArray.allIndexed(predicate: (index: Int, value: Int) -> Boolean): Boolean {
    var i = 0
    val iter = iterator()
    while (iter.hasNext()) {
        if (!predicate(i++, iter.next())) {
            return false
        }
    }
    return true
}


@State(Scope.Benchmark)
class JoinStreamBenchmark {

    private val left = mutableListOf<BitsetMapping>()
    private val right = mutableListOf<BitsetMapping>()
    private var leftStream: BitsetMappingStream? = null
    private var rightStream: BitsetMappingStream? = null

    @Setup
    fun createMappings() {
        val random = Random(1)
        repeat(SIZE) {
            val new = createMapping(random.nextInt())
            if (random.nextBoolean()) {
                left.add(new)
            } else {
                right.add(new)
            }
        }
        leftStream = left.toBitsetMappingStream()
        rightStream = right.toBitsetMappingStream()
    }

    @Benchmark
    fun regularJoinStream(): Stream<Mapping> {
        // cast required for the `join` method
        @Suppress("UNCHECKED_CAST")
        val a = left.toStream() as CollectedStream<Mapping>
        @Suppress("UNCHECKED_CAST")
        val b = right.toStream() as CollectedStream<Mapping>
        return a.join(b).collect()
    }

    @Benchmark
    fun arrJoinStream(): Stream<Mapping> {
        return BitsetMappingStream.join(leftStream!!, rightStream!!)
    }

}

private fun List<BitsetMapping>.toBitsetMappingStream(): BitsetMappingStream {
    val arr = VariableWidthBitsetMappingCollection()
    forEach { arr.add(it) }
    return BitsetMappingStream(arr)
}
