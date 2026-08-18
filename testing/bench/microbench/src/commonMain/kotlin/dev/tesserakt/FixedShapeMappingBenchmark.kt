package dev.tesserakt

import dev.tesserakt.sparql.runtime.collection.SimpleFixedShapeMappingArray
import dev.tesserakt.sparql.runtime.evaluation.mapping.BitsetMapping
import dev.tesserakt.sparql.runtime.evaluation.mapping.Mapping
import dev.tesserakt.sparql.runtime.stream.CollectedStream
import dev.tesserakt.sparql.runtime.stream.FixedShapeMappingArrayStream
import dev.tesserakt.sparql.runtime.stream.OptimisedStream
import dev.tesserakt.sparql.runtime.stream.join
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State
import kotlin.random.Random


private fun createMapping(
    mask: Int,
    random: Random,
): BitsetMapping {
    return BitsetMapping(
        bindings = mask,
        terms = IntArray(mask.countOneBits()) { random.nextInt(256) }
    )
}

@State(Scope.Benchmark)
class FixedShapeMappingBenchmark {

    private lateinit var left: OptimisedStream<Mapping>
    private lateinit var right: OptimisedStream<Mapping>

    private lateinit var leftFixed: OptimisedStream<Mapping>
    private lateinit var rightFixed: OptimisedStream<Mapping>

    @Setup
    fun createMappings() {
        val mappingLeft = arrayListOf<BitsetMapping>()
        val mappingRight = arrayListOf<BitsetMapping>()
        val random = Random(1)
        repeat(10_000) {
            // mask 0b011
            mappingLeft.add(createMapping(3, random))
            // mask 0b110
            mappingRight.add(createMapping(6, random))
        }
        left = CollectedStream(mappingLeft)
        right = CollectedStream(mappingRight)

        leftFixed = FixedShapeMappingArrayStream(SimpleFixedShapeMappingArray(3).also { it.addAll(mappingLeft) })
        rightFixed = FixedShapeMappingArrayStream(SimpleFixedShapeMappingArray(6).also { it.addAll(mappingRight) })
    }

    @Benchmark
    fun joinRegular(): List<Mapping> {
        return left.join(right).toList().also { println("GOT ${it.size}") }
    }

    @Benchmark
    fun joinFixedShape(): List<Mapping> {
        return leftFixed.join(rightFixed).toList().also { println("GOT ${it.size}") }
    }


}
