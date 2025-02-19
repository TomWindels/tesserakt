import dev.tesserakt.rdf.dsl.RDF
import dev.tesserakt.rdf.dsl.buildStore
import dev.tesserakt.rdf.dsl.extractPrefixes
import dev.tesserakt.rdf.serialization.util.BufferedString
import dev.tesserakt.rdf.serialization.util.wrapAsBufferedReader
import dev.tesserakt.rdf.trig.serialization.Deserializer
import dev.tesserakt.rdf.trig.serialization.TokenDecoder
import dev.tesserakt.rdf.trig.serialization.TokenEncoder
import dev.tesserakt.rdf.trig.serialization.TriGSerializer
import dev.tesserakt.rdf.types.Quad.Companion.asLiteralTerm
import dev.tesserakt.rdf.types.Quad.Companion.asNamedTerm
import dev.tesserakt.rdf.types.toStore
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue

class TriGSerialization {

    // based on https://www.w3.org/TR/trig/ example 1
    @Test
    fun serialize0() = serialize {
        val ex = prefix("ex", "http://www.example.org/vocabulary#")
        val document = prefix("", "http://www.example.org/exampleDocument#")
        graph(document("G1")) {
            val monica = document("Monica")
            monica has type being ex("Person")
            monica has ex("name") being "Monica Murphy".asLiteralTerm()
            monica has ex("homepage") being "http://www.monicamurphy.org".asNamedTerm()
            monica has ex("email") being "mailto:monica@monicamurphy.org".asNamedTerm()
            monica has ex("hasSkill") being multiple(
                ex("management"),
                ex("programming")
            )
        }
    }

    // smaller; testing inlining behaviour
    @Test
    fun serialize1() = serialize {
        graph("my-graph") {
            local("graph") has type being local("test")
        }
        local("data") has local("graph") being local("my-graph")
    }

    // smaller; testing inlining behaviour
    @Test
    fun serialize2() = serialize {
        val ex = prefix("ex", "http://www.example.org/")
        graph(ex("test")) {
            val stream = ex("stream")
            stream has type being ex("Stream")
            stream has ex("properties") being blank {
                type being ex("Properties")
                ex("value") being 10
                ex("name") being "Test".asLiteralTerm()
            }
        }
    }

    private fun serialize(block: RDF.() -> Unit) {
        val reference = buildStore(block = block)
        val prettyPrinted = TriGSerializer.serialize(reference, prefixes = block.extractPrefixes())
        println(prettyPrinted)
        // also checking the result by decoding it and comparing iterators, without prefixes as these are not added by
        //  the reference token encoder (the formatter does this)
        assertContentEquals(
            expected = TokenEncoder(reference).asIterable(),
            actual = TokenDecoder(
                BufferedString(
                    TriGSerializer.serialize(reference).wrapAsBufferedReader()
                )
            ).asIterable()
        )
        val complete = Deserializer(TokenDecoder(BufferedString(prettyPrinted.wrapAsBufferedReader())))
            .asIterable().toStore()
        val diffA1 = reference - complete
        val diffB1 = complete - reference
        assertTrue { diffA1.isEmpty() }
        assertTrue { diffB1.isEmpty() }
        // dropping the last line of the pretty printed output, which should result in missing data, which should cause
        //  an incomplete result
        val subset = prettyPrinted
            .lines()
            .dropLast(1)
            // making sure we're not cutting in the middle of a statement
            .dropLastWhile { it.isNotBlank() }
            .joinToString("\n")
        val incomplete = Deserializer(TokenDecoder(BufferedString(subset.wrapAsBufferedReader())))
            .asIterable().toStore()
        val diffA2 = reference - incomplete
        val diffB2 = incomplete - reference
        assertTrue { diffA2.isNotEmpty() }
        assertTrue { diffB2.isEmpty() }
        // TODO: another test case where we drop until reaching invalid input
    }

    // this is semantically not a proper iterable type, but it functions for our use case above
    private fun <T> Iterator<T>.asIterable(verbose: Boolean = false) = object : Iterable<T> {
        override fun iterator(): Iterator<T> = iterator {
            val iter = this@asIterable
            while (iter.hasNext()) {
                yield(iter.next().also { if (verbose) println("${this@asIterable::class.simpleName} yields $it") })
            }
        }
    }

}
