import dev.tesserakt.rdf.serialization.InternalSerializationApi
import dev.tesserakt.rdf.serialization.common.StringIterableSource
import kotlin.test.Test
import kotlin.test.assertEquals

class StringIterableSourceTest {

    @OptIn(InternalSerializationApi::class)
    @Test
    fun basic() {
        val input = listOf("abc", "def", "ghi")
        val source = StringIterableSource(input)
        val stream = source.open()
        assertEquals("ab", stream.read(2))
        assertEquals("cd", stream.read(2))
        assertEquals("ef", stream.read(2))
        assertEquals("gh", stream.read(2))
        assertEquals("i", stream.read(2))
        assertEquals(null, stream.read(2))
    }

    @OptIn(InternalSerializationApi::class)
    @Test
    fun smallChunks() {
        val input = listOf("a", "b", "c")
        val source = StringIterableSource(input)
        val stream = source.open()
        assertEquals("ab", stream.read(2))
        assertEquals("c", stream.read(2))
        assertEquals(null, stream.read(2))
    }

}
