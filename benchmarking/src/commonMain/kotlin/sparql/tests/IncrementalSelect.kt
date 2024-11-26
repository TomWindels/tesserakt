package sparql.tests

import dev.tesserakt.rdf.dsl.RdfContext.Companion.buildStore
import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.rdf.types.Quad.Companion.asNamedTerm
import dev.tesserakt.rdf.types.Store
import sparql.types.tests
import kotlin.random.Random
import kotlin.time.measureTime

/**
 * Generates a random graph with a series of relationships and creates a test with a matching query using that graph.
 * [depth] denotes the number of relations that make up a single "full" query (# of triple patterns with
 *  fixed predicates); setting this value to less than 3 would result in no cache being used at evaluation
 *  time (typically unwanted). The total amount of generated triples is [size] * [depth]. [entropy] affects how many
 *  subjects have to be generated, directly affecting the number of outputted bindings due to overlap: a higher
 *  [entropy] yields closer to [size] / 2 resulting bindings.
 */
fun compareIncrementalChainSelectOutput(size: Int = 500, depth: Int = 7, entropy: Float = 3f, seed: Int = Random.nextInt()) = tests {
    val store: Store
    val predicates = (0 ..< depth).map { "http://example.org/p_$it".asNamedTerm() }
    val rng = Random(seed)
    val prepTime = measureTime {
        store = buildStore {
            val subjects = (0 .. depth).map { d -> (0..< (entropy * size).toInt()).map { local("s_${d}_$it") } }
            repeat(size) {
                // whether to generate a guaranteed full set
                val isValid = rng.nextBoolean()
                if (isValid) {
                    var subject = subjects[0].random(rng)
                    repeat(depth) {
                        val next = subjects[it + 1].random(rng)
                        subject has predicates[it] being next
                        subject = next
                    }
                } else {
                    // not keeping track of the individual subjects being linked
                    repeat(depth) {
                        subjects[it].random(rng) has predicates[it] being subjects[it + 1].random(rng)
                    }
                }
            }
        }
    }
    println("Random data store built: generated ${store.size} triples with seed $seed ($prepTime)!")
    val query = buildString {
        appendLine("SELECT * {")
        repeat(depth) {
            appendLine("\t?s${it} <${predicates[it]}> ?s${it + 1} .")
        }
        append("}")
    }
    using(store) test query
}

/**
 * Generates a random graph with a series of relationships and creates a test with a matching query using that graph.
 * [depth] denotes the number of relationships generated for any given subject (and triple patterns in the query)
 */
fun compareIncrementalStarSelectOutput(size: Int = 200, depth: Int = 5, entropy: Float = 3f, seed: Int = Random.nextInt()) = tests {
    val store: Store
    val predicates: List<Quad.NamedTerm>
    val rng = Random(seed)
    val prepTime = measureTime {
        store = buildStore {
            val subjects = (0..< (entropy * size).toInt()).map { local("s_$it") }
            predicates = (0 ..< depth).map { "http://example.org/p_$it".asNamedTerm() }
            val objects = (0..< (entropy * size * depth).toInt()).map { local("o_$it") }
            repeat(size) {
                // whether to generate a guaranteed full set
                val isValid = rng.nextBoolean()
                if (isValid) {
                    val subject = subjects.random(rng)
                    repeat(depth) {
                        subject has predicates[it] being objects.random(rng)
                    }
                } else {
                    // not keeping track of the individual subjects being linked
                    repeat(depth) {
                        subjects.random(rng) has predicates[it] being objects.random(rng)
                    }
                }
            }
        }
    }
    println("Random data store built: generated ${store.size} triples with seed $seed ($prepTime)!")
    val query = buildString {
        appendLine("SELECT * {")
        repeat(depth) {
            appendLine("\t?s <${predicates[it]}> ?o${it + 1} .")
        }
        append("}")
    }
    using(store) test query
}
