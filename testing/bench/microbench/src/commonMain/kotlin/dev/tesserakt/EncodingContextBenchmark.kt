package dev.tesserakt

import dev.tesserakt.concurrent.globalTaskRunner
import dev.tesserakt.rdf.types.MutableEncodingContext
import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.rdf.types.concurrent
import dev.tesserakt.rdf.types.impl.TieredEncodingContextImpl
import dev.tesserakt.rdf.types.impl.concurrent
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State
import kotlin.math.absoluteValue
import kotlin.random.Random

@State(Scope.Benchmark)
class EncodingContextBenchmark {

    private lateinit var terms: List<CharArray>

    @Setup
    fun createTerms() {
        val r = Random(123)
        // we allow duplicates to exist as we want to mimic a typical datastore
        val domains = listOf(
            "http://example.org",
//            "https://example.org",
//            "http://www.example.org",
//            "https://www.example.org",
//            "http://example.com",
            "https://example.com",
//            "http://www.example.com",
//            "https://www.example.com",
//            "http://example.net",
//            "https://example.net",
            "http://www.example.net",
//            "https://www.example.net",
//            "http://test.org",
//            "https://test.org",
//            "http://www.test.org",
            "https://www.test.org",
//            "http://test.com",
//            "https://test.com",
//            "http://www.test.com",
//            "https://www.test.com",
//            "http://test.net",
//            "https://test.net",
//            "http://www.test.net",
//            "https://www.test.net",
//            "http://my-epic-domain.org",
//            "https://my-epic-domain.org",
//            "http://www.my-epic-domain.org",
//            "https://www.my-epic-domain.org",
//            "http://my-epic-domain.com",
//            "https://my-epic-domain.com",
//            "http://www.my-epic-domain.com",
//            "https://www.my-epic-domain.com",
//            "http://my-epic-domain.net",
//            "https://my-epic-domain.net",
//            "http://www.my-epic-domain.net",
//            "https://www.my-epic-domain.net",
        )
        val paths = List(50_000) {
            CharArray(r.nextInt(10, 20)) {
                ((r.nextInt().absoluteValue % 24) + 'a'.code).toChar()
            }.concatToString()
        }
        terms = List(1_000_000) {
            val domain = domains.random(r)
            val path = paths.random(r)
            "$domain/$path".toCharArray()
        }
    }

    @Benchmark
    fun regular(): Int {
        val context = MutableEncodingContext()
        val encoded = mutableSetOf<Int>()
        terms.forEach {
            encoded += context.encode(Quad.NamedTerm(it.concatToString()))
        }
        println(encoded.size)
        return encoded.size
    }

    @Benchmark
    fun tiered(): Int {
        val context = TieredEncodingContextImpl()
        val encoded = mutableSetOf<Int>()
        terms.forEach {
            encoded.add(context.encodeNamed(it))
        }
        println(encoded.size)
        return encoded.size
    }

    @Benchmark
    fun tieredConcurrent10(): Int {
        val context = TieredEncodingContextImpl.concurrent()
        val encoded = concurrentSet<Int>()
        // 10x 100k
        withMultithreading {
            List(10) { i ->
                globalTaskRunner.dispatch {
                    val range = i * 100_000 ..< (i + 1) * 100_000
                    range.forEach { i ->
                        encoded.add(context.encodeNamed(terms[i]))
                    }
                }
            }.forEach {
                it.await().getOrThrow()
            }
        }
        println(encoded.size)
        return encoded.size
    }

    @Benchmark
    fun tieredConcurrent2(): Int {
        val context = TieredEncodingContextImpl.concurrent()
        val encoded = concurrentSet<Int>()
        // 2x 500k
        withMultithreading {
            List(2) { i ->
                globalTaskRunner.dispatch {
                    val range = i * 500_000 ..< (i + 1) * 500_000
                    range.forEach { i ->
                        encoded.add(context.encodeNamed(terms[i]))
                    }
                }
            }.forEach {
                it.await().getOrThrow()
            }
        }
        println(encoded.size)
        return encoded.size
    }

    @Benchmark
    fun regularConcurrent10(): Int {
        val context = MutableEncodingContext.concurrent()
        val encoded = concurrentSet<Int>()
        // 10x 100k
        withMultithreading {
            List(10) { i ->
                globalTaskRunner.dispatch {
                    val range = i * 100_000 ..< (i + 1) * 100_000
                    range.forEach { i ->
                        encoded += context.encode(Quad.NamedTerm(terms[i].concatToString()))
                    }
                }
            }.forEach {
                it.await().getOrThrow()
            }
        }
        println(encoded.size)
        return encoded.size
    }

    @Benchmark
    fun regularConcurrent2(): Int {
        val context = MutableEncodingContext.concurrent()
        val encoded = concurrentSet<Int>()
        // 2x 500k
        withMultithreading {
            List(2) { i ->
                globalTaskRunner.dispatch {
                    val range = i * 500_000 ..< (i + 1) * 500_000
                    range.forEach { i ->
                        encoded += context.encode(Quad.NamedTerm(terms[i].concatToString()))
                    }
                }
            }.forEach {
                it.await().getOrThrow()
            }
        }
        println(encoded.size)
        return encoded.size
    }

}

expect inline fun withMultithreading(block: () -> Unit)

expect fun <T> concurrentSet(): MutableSet<T>
