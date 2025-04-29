
import dev.tesserakt.rdf.dsl.RDF
import dev.tesserakt.rdf.dsl.buildStore
import dev.tesserakt.rdf.dsl.extractPrefixes
import dev.tesserakt.rdf.serialization.DelicateSerializationApi
import dev.tesserakt.rdf.serialization.common.TextDataSource
import dev.tesserakt.rdf.serialization.common.collect
import dev.tesserakt.rdf.serialization.util.BufferedString
import dev.tesserakt.rdf.turtle.serialization.*
import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.rdf.types.Quad.Companion.asLiteralTerm
import dev.tesserakt.rdf.types.Quad.Companion.asNamedTerm
import dev.tesserakt.rdf.types.toStore
import dev.tesserakt.testing.comparisonOf
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue

class TurtleSerialization {

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
        val ex = prefix("ex", "http://example.org/")
        graph(ex("my-graph")) {
            ex("graph") has type being ex("Test")
        }
        ex("data") has ex("graph") being ex("my-graph")
    }

    // testing blank object behaviour
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

    // testing escape sequences
    @Test
    fun serialize3() = serialize {
        val ex = prefix("ex", "http://www.example.org/")
        graph(ex("t#st")) {
            val stream = ex("my_stream")
            stream has type being ex("Stream")
            stream has ex("value") being """This\should_not#be+escaped""".asLiteralTerm()
            // should be a valid prefix term w/o any escaping for the % sign, see https://www.w3.org/TR/turtle/#h_note_5
            stream has ex("encoded_sequence") being ex("%AB-test")
        }
    }

    @OptIn(DelicateSerializationApi::class)
    private fun serialize(block: RDF.() -> Unit) {
        val reference = buildStore(block = block)
        val serializer = turtle {
            usePrettyFormatting {
                withPrefixes(block.extractPrefixes())
                withDynamicIndent()
            }
        }
        val prettyPrinted = serializer.serialize(reference.iterator()).collect()
        println(prettyPrinted)
        // also checking the result by decoding it and comparing iterators, without prefixes as these are not added by
        //  the reference token encoder (the formatter does this)
        assertContentEquals(
            expected = TokenEncoder(reference.iterator()).asIterable(),
            actual = TokenDecoder(
                BufferedString(
                    TextDataSource(TurtleSerializer.serialize(reference.iterator()).collect()).open()
                )
            ).asIterable()
        )
        val complete = Deserializer(TokenDecoder(BufferedString(TextDataSource(prettyPrinted).open())))
            .asIterable().toStore()
        // as turtle doesn't contain graphs, every read-in quad should have the default graph
        val r = reference.map { it.copy(g = Quad.DefaultGraph) }.toStore()
        var comparison = comparisonOf(
            a = r,
            b = complete
        )
        assertTrue(comparison.isIdentical(), comparison.toString())
        // dropping the last line of the pretty printed output, which should result in missing data, which should cause
        //  an incomplete result
        val subset = prettyPrinted
            .lines()
            .dropLast(1)
            // making sure we're not cutting in the middle of a statement
            .dropLastWhile { it.isNotBlank() }
            .joinToString("\n")
        val incomplete = Deserializer(TokenDecoder(BufferedString(TextDataSource(subset).open())))
            .asIterable().toStore()
        comparison = comparisonOf(
            a = r,
            b = incomplete
        )
        assertTrue(comparison.missing.isNotEmpty() && comparison.leftOver.isEmpty(), comparison.toString())
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
