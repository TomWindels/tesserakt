package sparql.tests

import dev.tesserakt.rdf.dsl.RdfContext.Companion.buildStore
import dev.tesserakt.rdf.types.Quad.Companion.asNamedTerm
import dev.tesserakt.rdf.types.Store
import test.suite.testEnv
import kotlin.random.Random
import kotlin.time.measureTime

/**
 * Generates a random graph with a series of relationships and creates a test with a matching query using that graph.
 * [depth] denotes the number of relations that make up a single "full" query (# of triple patterns with
 *  fixed predicates); setting this value to less than 3 would result in no cache being used at evaluation
 *  time (typically unwanted). The total amount of generated triples is [size] * [depth]
 */
fun compareIncrementalSelectOutput(size: Int = 200, depth: Int = 5) = testEnv {
    val store: Store
    val predicates = (0 ..< depth).map { "http://example.org/p_$it".asNamedTerm() }
    val prepTime = measureTime {
        store = buildStore {
            val subjects = (0 .. depth).map { d -> (0..< 3 * size).map { local("s_${d}_$it") } }
            repeat(size) {
                // whether to generate a guaranteed full set
                val isValid = Random.nextBoolean()
                if (isValid) {
                    var subject = subjects[0].random()
                    repeat(depth) {
                        val next = subjects[it + 1].random()
                        subject has predicates[it] being next
                        subject = next
                    }
                } else {
                    // not keeping track of the individual subjects being linked
                    repeat(depth) {
                        subjects[it].random() has predicates[it] being subjects[it + 1].random()
                    }
                }
            }
        }
    }
    println("Random data store built: generated ${store.size} triples ($prepTime)!")
    val query = buildString {
        appendLine("SELECT * {")
        repeat(depth) {
            appendLine("\t?s${it} <${predicates[it]}> ?s${it + 1} .")
        }
        append("}")
    }
    using(store) test query
}
