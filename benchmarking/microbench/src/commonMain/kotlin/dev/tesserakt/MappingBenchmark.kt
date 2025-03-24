package dev.tesserakt

import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.rdf.types.Quad.Companion.asLiteralTerm
import dev.tesserakt.rdf.types.Quad.Companion.asNamedTerm
import dev.tesserakt.sparql.runtime.evaluation.GlobalQueryContext
import dev.tesserakt.sparql.runtime.evaluation.Mapping
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State
import kotlin.random.Random

const val SIZE = 7_500
const val VARIANCE = 50
val BINDINGS = listOf(
    "person" to List(VARIANCE) { "http://example/person_${it}".asNamedTerm() },
    "job" to List(VARIANCE) { "http://example/job_${it}".asNamedTerm() },
    "name" to List(VARIANCE) { "http://example/name_${it}".asNamedTerm() },
    "age" to List(VARIANCE) { it.asLiteralTerm() },
)

typealias MapMapping = Map<String, Quad.Term>

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
    private lateinit var l: List<Mapping>
    private lateinit var r: List<Mapping>
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
        l = left.map { Mapping(context, it) }
        r = right.map { Mapping(context, it) }
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
