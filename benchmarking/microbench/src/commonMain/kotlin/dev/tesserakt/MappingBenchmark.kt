package dev.tesserakt

import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.rdf.types.Quad.Companion.asLiteralTerm
import dev.tesserakt.rdf.types.Quad.Companion.asNamedTerm
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State
import kotlin.jvm.JvmInline
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

///////

@JvmInline
value class NewMapping(private val data: IntArray) {

    constructor(source: Map<String, Quad.Term>): this(data = convert(source))

    fun join(other: NewMapping): NewMapping? {
        return combine(data, other.data)?.let { NewMapping(it) }
    }

    fun isCompatibleWith(other: NewMapping): Boolean {
        return count(this.data, other.data) != -1
    }

    companion object {

        private val BINDING_LUT = mutableMapOf<String, Int>()
        private val TERM_LUT = mutableMapOf<Quad.Term, Int>()

        private fun convert(input: Map<String, Quad.Term>): IntArray {
            return input.map { resolveBinding(it.key) to resolveTerm(it.value) }.sortedBy { it.first }.flatten()
        }

        private fun resolveBinding(name: String): Int {
            return BINDING_LUT.getOrPut(name) { BINDING_LUT.size }
        }

        private fun resolveTerm(term: Quad.Term): Int {
            return TERM_LUT.getOrPut(term) { TERM_LUT.size }
        }

        private fun List<Pair<Int, Int>>.flatten(): IntArray {
            val result = IntArray(size * 2)
            forEachIndexed { i, (a, b) ->
                result[2 * i] = a
                result[2 * i + 1] = b
            }
            return result
        }

        /**
         * Counts the total number of bindings that would be part of this mapping, or -1 if incompatible
         */
        private fun count(left: IntArray, right: IntArray): Int {
            if (left.isEmpty()) {
                return right.size / 2
            }
            if (right.isEmpty()) {
                return left.size / 2
            }
            val a = left.iterator()
            val b = right.iterator()
            var l = a.nextInt()
            var r = b.nextInt()
            var count = 0
            while (true) {
                when {
                    l < r -> {
                        count += 1
                        a.nextInt()
                        if (!a.hasNext()) {
                            break
                        }
                        l = a.nextInt()
                    }
                    r < l -> {
                        count += 1
                        b.nextInt()
                        if (!b.hasNext()) {
                            break
                        }
                        r = b.nextInt()
                    }
                    else /* == */ -> {
                        if (a.nextInt() != b.nextInt()) {
                            return -1
                        }
                        if (!a.hasNext() || !b.hasNext()) {
                            break
                        }
                        l = a.nextInt()
                        r = b.nextInt()
                        ++count
                    }
                }
            }
            while (a.hasNext()) {
                ++count
                a.nextInt()
                a.nextInt()
            }
            while (b.hasNext()) {
                ++count
                b.nextInt()
                b.nextInt()
            }
            return count
        }

        private fun combine(left: IntArray, right: IntArray): IntArray? {
            val size = count(left, right)
            if (size == -1) {
                return null
            }
            val result = IntArray(size * 2)
            val a = left.iterator()
            val b = right.iterator()
            var l = a.nextInt()
            var r = b.nextInt()
            var i = 0
            while (a.hasNext() && b.hasNext()) {
                while (true) {
                    when {
                        l < r -> {
                            result[i] = l
                            result[i + 1] = a.nextInt()
                            if (!a.hasNext()) {
                                break
                            }
                            l = a.nextInt()
                        }
                        r < l -> {
                            result[i] = r
                            result[i + 1] = b.nextInt()
                            if (!b.hasNext()) {
                                break
                            }
                            r = b.nextInt()
                        }
                        else /* == */ -> {
                            result[i] = l
                            result[i + 1] = a.nextInt()
                            require(result[i] == b.nextInt())
                            if (!a.hasNext() || !b.hasNext()) {
                                break
                            }
                            l = a.nextInt()
                            r = b.nextInt()
                        }
                    }
                    i += 2
                }
                while (a.hasNext()) {
                    result[i] = a.nextInt()
                    result[i + 1] = a.nextInt()
                    i += 2
                }
                while (b.hasNext()) {
                    result[i] = b.nextInt()
                    result[i + 1] = b.nextInt()
                    i += 2
                }
            }
            return result
        }

    }
}

fun MapMapping.toNewMapping() = NewMapping(this)

///////

@State(Scope.Benchmark)
class MappingBenchmark {

    private val left = mutableListOf<MapMapping>()
    private val right = mutableListOf<MapMapping>()

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
    }

    @Benchmark
    fun joinRegular(): List<MapMapping> {
        return left.flatMap { l -> right.mapNotNull { r -> join(l, r) } }
    }

    @Benchmark
    fun joinNew(): List<MapMapping> {
        val l = left.map { it.toNewMapping() }
        val r = right.map { it.toNewMapping() }
        return l.flatMap { l -> r.mapNotNull { r -> l.join(r) } }.map { it.toMapMapping() }
    }

}
