import dev.tesserakt.rdf.serialization.util.EscapeSequenceHelper
import dev.tesserakt.rdf.serialization.util.appendCodePoint
import kotlin.test.Test
import kotlin.test.assertEquals

class EscapeSequenceTest {

    @OptIn(ExperimentalStdlibApi::class)
    @Test
    fun escape1() {
        ('a'..'z').forEach { char ->
            val input = "\\u${char.code.toHexString().takeLast(4)}"
            val decoded = buildString {
                appendCodePoint(EscapeSequenceHelper.hexToInt(input[2], input[3], input[4], input[5]))
            }
            assertEquals(char.toString(), decoded)
        }
    }

    @Test
    fun escape2() {
        // https://www.fileformat.info/info/unicode/char/0371/index.htm
        val input = "0371"
        val decoded = buildString {
            appendCodePoint(EscapeSequenceHelper.hexToInt(input[0], input[1], input[2], input[3]))
        }
        assertEquals("ͱ", decoded)
    }

    @Test
    fun escape3() {
        // https://www.fileformat.info/info/unicode/char/0371/index.htm
        val input = "2654"
        val decoded = buildString {
            appendCodePoint(EscapeSequenceHelper.hexToInt(input[0], input[1], input[2], input[3]))
        }
        assertEquals("♔", decoded)
    }

    @Test
    fun escape4() {
        // https://www.fileformat.info/info/unicode/char/0371/index.htm
        val input = "0002f804"
        val decoded = buildString {
            appendCodePoint(EscapeSequenceHelper.hexToInt(input[0], input[1], input[2], input[3], input[4], input[5], input[6], input[7]))
        }
        assertEquals("\uD87E\uDC04", decoded)
    }

}
