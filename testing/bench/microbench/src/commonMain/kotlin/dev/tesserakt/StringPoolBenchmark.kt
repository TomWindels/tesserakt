package dev.tesserakt

import dev.tesserakt.rdf.ontology.RDF
import dev.tesserakt.rdf.ontology.SHACL
import dev.tesserakt.rdf.ontology.XSD
import dev.tesserakt.util.CommonPrefixStringPool
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State
import kotlin.random.Random

private const val TEST_SIZE = 10_000
private val prefixes = listOf(
    XSD.prefix,
    RDF.prefix,
    SHACL.prefix,
    "http://example.org/",
    "http://mysite.com/",
)

@State(Scope.Benchmark)
class StringPoolBenchmark {

    lateinit var uris: List<String>

    @Setup
    fun createUris() {
        uris = buildList {
            val random = Random(0)
            repeat(TEST_SIZE) {
                add(generateUri(random))
            }
        }
    }

    @Benchmark
    fun prefixPool(): List<CommonPrefixStringPool.Handle> {
        val pool = CommonPrefixStringPool()
        return uris.map { pool.createHandle(it) }
    }

    // a simple pool that simply deduplicates exact string matches, but with no extra memory
    //  benefit
    @Benchmark
    fun uriSet(): Set<String> {
        return uris.toSet()
    }

    // not really a pool, but does ensure strings that start with the same character sequence
    //  are closes together in memory, upon which additional optimisation could be implemented,
    //  and thus could be considered a relevant time reference
    @Benchmark
    fun sortedUris(): List<String> {
        return uris.sorted()
    }

    private fun generateUri(random: Random): String {
        val bytes = random.nextBytes(random.nextInt() % 5 + 20)
        repeat (bytes.size) {
            // 'a' - 'z'
            bytes[it] = (bytes[it] % 12 + 109).toByte()
        }
        return prefixes.random(random) + bytes
    }

}
