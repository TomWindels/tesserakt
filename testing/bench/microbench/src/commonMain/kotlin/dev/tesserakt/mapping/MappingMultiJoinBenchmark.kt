package dev.tesserakt.mapping

import dev.tesserakt.sparql.runtime.evaluation.Mapping
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State

@State(Scope.Benchmark)
class MappingMultiJoinBenchmark {

    private lateinit var one: List<Mapping>
    private lateinit var two: List<Mapping>
    private lateinit var three: List<Mapping>
    private lateinit var four: List<Mapping>

    @Setup
    fun createMappings() {
        one = generateMappingSet(1).take(30).map { it.toMapping() }
        two = generateMappingSet(2).take(30).map { it.toMapping() }
        three = generateMappingSet(3).take(30).map { it.toMapping() }
        four = generateMappingSet(4).take(30).map { it.toMapping() }
    }

    @Benchmark
    fun joinRegular(): List<Mapping> {
        val result = one
            .flatMap { left -> two.mapNotNull { right -> left.join(right) } }
            .flatMap { left -> three.mapNotNull { right -> left.join(right) } }
            .flatMap { left -> four.mapNotNull { right -> left.join(right) } }
        println("OLD - Got ${result.size} mapping(s)")
        return result
    }

    @Benchmark
    fun joinNew(): List<Mapping> {
        val s = MutableList<Mapping?>(4) { null }
        val result = buildList {
            one.forEach { o ->
                s[0] = o
                two.forEach { t ->
                    s[1] = t
                    three.forEach { r ->
                        s[2] = r
                        four.forEach { f ->
                            s[3] = f
                            val joined = Mapping(s as List<Mapping>)
                            if (joined != null) {
                                add(joined)
                            }
                        }
                    }
                }
            }
        }
        println("NEW - Got ${result.size} mapping(s)")
        return result
    }

}
