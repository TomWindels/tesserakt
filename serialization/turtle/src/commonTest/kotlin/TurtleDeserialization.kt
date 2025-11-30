import dev.tesserakt.rdf.ontology.XSD
import dev.tesserakt.rdf.serialization.DelicateSerializationApi
import dev.tesserakt.rdf.serialization.InternalSerializationApi
import dev.tesserakt.rdf.serialization.common.DataSource
import dev.tesserakt.rdf.serialization.common.DeserializationException
import dev.tesserakt.rdf.serialization.common.serializer
import dev.tesserakt.rdf.serialization.core.DataStream
import dev.tesserakt.rdf.serialization.core.TextDataStream
import dev.tesserakt.rdf.turtle.serialization.Turtle
import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.rdf.types.toStore
import kotlin.test.*

class TurtleDeserialization {

    @OptIn(InternalSerializationApi::class)
    private class TestSource(private val data: String): DataSource {

        private class Stream(data: String): DataStream {
            @OptIn(DelicateSerializationApi::class)
            private val inner = TextDataStream(data)

            var closed = false
                private set

            override fun read(target: CharArray, offset: Int, count: Int): Int {
                return inner.read(target, offset, count)
            }

            override fun close() {
                closed = true
                inner.close()
            }
        }

        private val streams = mutableListOf<Stream>()

        override fun open(): DataStream {
            return Stream(data).also { streams.add(it) }
        }

        fun assertClosed() {
            assertTrue("No streams were opened!") { streams.isNotEmpty()}
            val opened = streams.count { !it.closed }
            assertEquals(0, opened, "Not all streams were closed!")
        }

    }

    @OptIn(DelicateSerializationApi::class)
    @Test
    fun badInput() {
        val input = """
            <http://example.org/User> <http://example.org/name "User" .
        """
        val serializer = serializer(Turtle)
        val source = TestSource(input)
        assertFailsWith<DeserializationException> { serializer.deserialize(source).toStore() }
            .also { it.printStackTrace() }
        source.assertClosed()
    }

    @OptIn(DelicateSerializationApi::class)
    @Test
    fun goodInput() {
        val input = """
            <http://example.org/User> <http://example.org/name> "User" .
        """
        val serializer = serializer(Turtle)
        val source = TestSource(input)
        val store = serializer.deserialize(source).toStore()
        source.assertClosed()
        assertContains(store, Quad(Quad.NamedTerm("http://example.org/User"), Quad.NamedTerm("http://example.org/name"), Quad.Literal("User", XSD.string)))
    }

}
