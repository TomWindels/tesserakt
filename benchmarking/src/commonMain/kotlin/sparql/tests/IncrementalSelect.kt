package sparql.tests

import dev.tesserakt.rdf.dsl.RdfContext.Companion.buildStore
import dev.tesserakt.rdf.types.Quad.Companion.asNamedTerm
import dev.tesserakt.rdf.types.Store
import dev.tesserakt.testing.testEnv
import sparql.types.using
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
fun compareIncrementalSelectOutput(size: Int = 200, depth: Int = 5, entropy: Float = 3f) = testEnv {
    val store: Store
    val predicates = (0 ..< depth).map { "http://example.org/p_$it".asNamedTerm() }
    val prepTime = measureTime {
        store = buildStore {
            val subjects = (0 .. depth).map { d -> (0..< (entropy * size).toInt()).map { local("s_${d}_$it") } }
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
    val query1 = buildString {
        appendLine("SELECT * {")
        repeat(depth) {
            appendLine("\t?s${it} <${predicates[it]}> ?s${it + 1} .")
        }
        append("}")
    }
    val query2 = buildString {
        appendLine("SELECT * {")
        repeat(depth) {
            val reversed = depth - it - 1
            appendLine("\t?s${reversed} <${predicates[reversed]}> ?s${reversed + 1} .")
        }
        append("}")
    }
    val query3 = buildString {
        appendLine("SELECT * {")
        repeat(depth) {
            if (it % 2 == 0) {
                appendLine("\t?s${it} <${predicates[it]}> ?s${it + 1} .")
            } else {
                val reversed = depth - it - 1
                appendLine("\t?s${reversed} <${predicates[reversed]}> ?s${reversed + 1} .")
            }
        }
        append("}")
    }
    println("Evaluation queries:\n${query1.prependIndent("\t")}\n${query2.prependIndent("\t")}\n${query3.prependIndent("\t")}")
    using(store) test query1
    using(store) test query2
    using(store) test query3
}
