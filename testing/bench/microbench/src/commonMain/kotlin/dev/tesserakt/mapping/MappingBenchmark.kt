package dev.tesserakt.mapping

import dev.tesserakt.sparql.runtime.evaluation.Mapping
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State
import kotlin.random.Random


private fun createMapping(id: Int): MapMapping {
    val rng = Random(id)
    return BINDINGS.associate { it.first to it.second.random(rng) }.filter { rng.nextBoolean() }
}

private inline fun <K: Any, V: Any> Map<K, V>.compatibleWith(reference: Map<K, V>) =
    reference.all { (refKey, refValue) -> val data = this[refKey]; data == null || data == refValue}


private fun join(a: MapMapping, b: MapMapping): MapMapping? {
    return if (a.compatibleWith(b)) {
        a + b
    } else {
        null
    }
}

@State(Scope.Benchmark)
class MappingBenchmark {

    private lateinit var left: List<MapMapping>
    private lateinit var right: List<MapMapping>
    private lateinit var l: List<Mapping>
    private lateinit var r: List<Mapping>

    @Setup
    fun createMappings() {
        left = generateMappingSet(1)
        right = generateMappingSet(2)
        l = left.map { it.toMapping() }
        r = right.map { it.toMapping() }
    }

    @Benchmark
    fun joinRegular(): List<MapMapping> {
        return left.flatMap { l -> right.mapNotNull { r -> join(l, r) } }
            .also { println("Result size regular: ${it.size}") }
    }

    @Benchmark
    fun joinNew(): List<Mapping> {
        return l.flatMap { l -> r.mapNotNull { r -> l.join(r) } }
            .also { println("Result size new: ${it.size}") }
    }

}
