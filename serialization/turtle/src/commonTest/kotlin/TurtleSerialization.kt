import dev.tesserakt.rdf.dsl.RDFContext
import dev.tesserakt.rdf.dsl.buildStore
import dev.tesserakt.rdf.dsl.extractPrefixes
import dev.tesserakt.rdf.serialization.DelicateSerializationApi
import dev.tesserakt.rdf.serialization.InternalSerializationApi
import dev.tesserakt.rdf.serialization.common.TextDataSource
import dev.tesserakt.rdf.serialization.common.serializer
import dev.tesserakt.rdf.serialization.turtle.*
import dev.tesserakt.rdf.serialization.util.BufferedString
import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.rdf.types.Quad.NamedTerm
import dev.tesserakt.rdf.types.toStore
import dev.tesserakt.testing.unorderedComparisonOf
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue

class TurtleSerialization {

    // based on https://www.w3.org/TR/trig/ example 1
    @Test
    fun serialize0() = serialize {
        val ex = prefix("ex", "http://www.example.org/vocabulary#")
        val document = prefix("", "http://www.example.org/exampleDocument#")
        (document / "G1") {
            val monica = document / "Monica"
            monica a (ex / "Person")
            monica (ex / "name") (Quad.Literal("Monica Murphy"))
            monica (ex / "homepage") (NamedTerm("http://www.monicamurphy.org"))
            monica (ex / "email") (NamedTerm("mailto:monica@monicamurphy.org"))
            monica (ex / "hasSkill") (
                ex / "management",
                ex / "programming"
            )
        }
    }

    // smaller; testing inlining behaviour
    @Test
    fun serialize1() = serialize {
        val ex = prefix("ex", "http://example.org/")
        (ex / "my-graph") {
            (ex / "graph") a (ex / "Test")
        }
        (ex / "data") (ex / "graph") (ex / "my-graph")
    }

    // testing blank object behaviour
    @Test
    fun serialize2() = serialize {
        val ex = prefix("ex", "http://www.example.org/")
        (ex / "test") {
            val stream = (ex / "stream")
            stream a ex / "Stream"
            stream (ex / "properties") blank {
                a (ex / "Properties")
                (ex / "value") (10)
                (ex / "name") (Quad.Literal("Test"))
            }
        }
    }

    // testing escape sequences
    @Test
    fun serialize3() = serialize {
        val ex = prefix("ex", "http://www.example.org/")
        (ex / "t#st") {
            val stream = ex / "my_stream"
            stream a ex / "Stream"
            (stream) (ex / "value") (Quad.Literal("""This\should_not#be+escaped"""))
            // should be a valid prefix term w/o any escaping for the % sign, see https://www.w3.org/TR/turtle/#h_note_5
            (stream) (ex / "encoded_sequence") (ex / "%AB-test")
        }
    }

    @OptIn(DelicateSerializationApi::class, InternalSerializationApi::class)
    private fun serialize(block: RDFContext.() -> Unit) {
        val reference = buildStore(block = block)
        val serializer = serializer(Turtle) {
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
            expected = TurtleTokenEncoder(reference.iterator()).asIterable(),
            actual = TurtleTokenDecoder(
                BufferedString(
                    TextDataSource(TurtleSerializer.serialize(reference.iterator()).collect()).open()
                )
            ).asIterable()
        )
        val complete = TurtleDeserializer(TurtleTokenDecoder(BufferedString(TextDataSource(prettyPrinted).open())))
            .asIterable().toStore()
        // as turtle doesn't contain graphs, every read-in quad should have the default graph
        val r = reference.map { it.copy(g = Quad.DefaultGraph) }.toStore()
        var comparison = unorderedComparisonOf(
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
        val incomplete = TurtleDeserializer(TurtleTokenDecoder(BufferedString(TextDataSource(subset).open())))
            .asIterable().toStore()
        comparison = unorderedComparisonOf(
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

    private fun Iterator<String>.collect(): String = buildString {
        this@collect.forEach { segment -> append(segment) }
    }

}
