import dev.tesserakt.rdf.serialization.InternalSerializationApi
import dev.tesserakt.rdf.serialization.core.DataStream
import dev.tesserakt.rdf.serialization.core.TextDataStream
import dev.tesserakt.rdf.serialization.util.CircularCharBuffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse

class CircularCharBufferTest {

    @OptIn(InternalSerializationApi::class)
    class TestSource(data: String): DataStream {

        @OptIn(InternalSerializationApi::class)
        private val inner = TextDataStream(data)

        override fun read(target: CharArray, offset: Int, count: Int): Int {
            println("* Processing read request: offset $offset, count $count...")
            return inner.read(target, offset, count).also { println("> Resulting count is $it") }
        }

        override fun close() { /* nothing to do */ }
    }

    @OptIn(InternalSerializationApi::class)
    @Test
    fun flushing() {
        val source = TestSource("0123456789")
        val buf = CircularCharBuffer(4)
        assertFails { buf[0] }
        println("Buf before read:\n$buf")
        buf.read(source)
        println("Buf after first read:\n$buf")
        assertEquals('0', buf[0])
        assertEquals('1', buf[1])
        assertEquals('2', buf[2])
        assertEquals('3', buf[3])
        buf.consume(4)
        println("Buf after first consume:\n$buf")
        buf.read(source)
        println("Buf after second read:\n$buf")
        assertEquals('4', buf[0])
        assertEquals('5', buf[1])
        assertEquals('6', buf[2])
        assertEquals('7', buf[3])
        buf.consume(4)
        println("Buf after second consume:\n$buf")
        buf.read(source)
        println("Buf after third read:\n$buf")
        assertEquals('8', buf[0])
        assertEquals('9', buf[1])
        assertFalse { buf.read(source) }
    }

    @OptIn(InternalSerializationApi::class)
    @Test
    fun rolling() {
        val source = TestSource("0123456789")
        val buf = CircularCharBuffer(4)
        assertFails { buf[0] }
        println("Buf before read:\n$buf")
        buf.read(source)
        println("Buf after first read:\n$buf")
        assertEquals('0', buf[0])
        assertEquals('1', buf[1])
        assertEquals('2', buf[2])
        assertEquals('3', buf[3])
        buf.consume(3)
        println("Buf after first consume:\n$buf")
        buf.read(source)
        println("Buf after second read:\n$buf")
        assertEquals('3', buf[0])
        assertEquals('4', buf[1])
        buf.consume(2)
        println("Buf after second consume:\n$buf")
        buf.read(source)
        println("Buf after third read:\n$buf")
        assertEquals('5', buf[0])
        assertEquals('6', buf[1])
        assertEquals('7', buf[2])
        buf.consume(3)
        println("Buf after third consume:\n$buf")
        buf.read(source)
        println("Buf after fourth read:\n$buf")
        assertEquals('8', buf[0])
        assertEquals('9', buf[1])
        assertFails { buf.consume(4) }
        assertFalse { buf.read(source) }
    }

    @OptIn(InternalSerializationApi::class)
    @Test
    fun streaming() {
        val CAPACITY = 8
        val buf = CircularCharBuffer(CAPACITY)
        val input = TestSource(CharArray(1000) { Char(it) }.concatToString())
        buf.read(input)
        repeat(1000 - CAPACITY) { i ->
            repeat(CAPACITY) { j ->
                assertEquals(i + j, buf[j].code, "Failed at $i, $j: $buf")
            }
            buf.consume(1)
            buf.read(input)
        }
        println("Buf: $buf")
        repeat(CAPACITY) { k ->
            assertEquals(1000 - CAPACITY + k, buf[0].code, "Failed after $k consume call(s), expecting ${1000 - CAPACITY + k}: $buf")
            buf.consume(1)
            buf.read(input)
        }
    }

}
