import dev.tesserakt.rdf.serialization.InternalSerializationApi
import dev.tesserakt.rdf.serialization.common.StringIterableSource
import dev.tesserakt.rdf.serialization.core.DataStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class StringIterableSourceTest {

    @OptIn(InternalSerializationApi::class)
    @Test
    fun basic() {
        val input = listOf("abc", "def", "ghi")
        val source = StringIterableSource(input)
        val stream = source.open()
        val buf = CharArray(2)
        listOf("ab", "cd", "ef", "gh", "i").forEach { expected ->
            assertEquals(expected.length, stream.readExact(buf, 2))
            assertContentEquals(expected.toCharArray(), buf.sliceArray(0 ..< expected.length))
        }
        assertEquals(0, stream.readExact(buf, 2))
    }

    @OptIn(InternalSerializationApi::class)
    @Test
    fun smallChunks() {
        val input = listOf("a", "b", "c")
        val source = StringIterableSource(input)
        val stream = source.open()
        val buf = CharArray(2)
        listOf("ab", "c").forEach { expected ->
            assertEquals(expected.length, stream.readExact(buf, 2))
            assertContentEquals(expected.toCharArray(), buf.sliceArray(0 ..< expected.length))
        }
        assertEquals(0, stream.readExact(buf, 2))
    }

    /**
     * Repeatedly invokes [DataStream.read] until at [count] characters are read into the [target], starting from
     *  offset 0. Returns the actual amount read, which can be less than [count] if the end has been reached
     */
    @OptIn(InternalSerializationApi::class)
    private fun DataStream.readExact(target: CharArray, count: Int): Int {
        var read = 0
        while (read < count) {
            val new = read(target, read, count - read)
            if (new < 0) {
                break
            }
            read += new
        }
        return read
    }

}
