package dev.tesserakt

import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.rdf.types.Quad.NamedTerm
import dev.tesserakt.sparql.runtime.evaluation.context.GlobalQueryContext
import dev.tesserakt.sparql.runtime.evaluation.mapping.Mapping
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State
import kotlin.random.Random

const val SIZE = 7_500
const val VARIANCE = 50
val BINDINGS = listOf(
    "person" to List(VARIANCE) { NamedTerm("http://example/person_${it}") },
    "job" to List(VARIANCE) { NamedTerm("http://example/job_${it}") },
    "name" to List(VARIANCE) { NamedTerm("http://example/name_${it}") },
    "age" to List(VARIANCE) { Quad.Literal(it) },
)

typealias MapMapping = Map<String, Quad.Element>

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

    private val left = mutableListOf<MapMapping>()
    private val right = mutableListOf<MapMapping>()
    private lateinit var mappingLeft: List<Mapping>
    private lateinit var mappingRight: List<Mapping>
    private val context = GlobalQueryContext

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
        mappingLeft = left.map { Mapping(context, it) }
        mappingRight = right.map { Mapping(context, it) }
    }

//    @Benchmark
    fun joinRegular(): List<MapMapping> {
        return left.flatMap { l -> right.mapNotNull { r -> join(l, r) } }
            .also { println("Result size regular: ${it.size}") }
    }

//    @Benchmark
    fun joinMapping(): List<Mapping> {
        return mappingLeft.flatMap { l -> mappingRight.mapNotNull { r -> l.join(r) } }
            .also { println("Result size new 2: ${it.size}") }
    }

}
