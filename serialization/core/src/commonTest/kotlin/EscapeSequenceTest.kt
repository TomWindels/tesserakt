import dev.tesserakt.rdf.serialization.util.EscapeSequenceHelper
import kotlin.test.Test
import kotlin.test.assertEquals

class EscapeSequenceTest {

    @OptIn(ExperimentalStdlibApi::class)
    @Test
    fun escape1() {
        ('a'..'z').forEach { char ->
            val input = "\\u${char.code.toHexString().takeLast(4)}"
            val decoded = EscapeSequenceHelper.decodeNumericEscapes(input)
            assertEquals(char.toString(), decoded)
        }
    }

    @Test
    fun escape2() {
        // https://www.fileformat.info/info/unicode/char/0371/index.htm
        val input = "My decoded string: \\u0371"
        val decoded = EscapeSequenceHelper.decodeNumericEscapes(input)
        assertEquals("My decoded string: ͱ", decoded)
    }

    @Test
    fun escape3() {
        // https://www.fileformat.info/info/unicode/char/0371/index.htm
        val input = "My decoded string: \\u2654"
        val decoded = EscapeSequenceHelper.decodeNumericEscapes(input)
        assertEquals("My decoded string: ♔", decoded)
    }

    @Test
    fun escape4() {
        // https://www.fileformat.info/info/unicode/char/0371/index.htm
        val input = "My decoded string: \\U0002f804"
        val decoded = EscapeSequenceHelper.decodeNumericEscapes(input)
        assertEquals("My decoded string: \uD87E\uDC04", decoded)
    }

}
