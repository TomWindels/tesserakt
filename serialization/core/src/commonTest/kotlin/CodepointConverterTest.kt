
import dev.tesserakt.rdf.serialization.util.codepointToString
import kotlin.test.Test
import kotlin.test.assertEquals

class CodepointConverterTest {

    @Test
    fun escape1() {
        ('a'..'z').forEach { char ->
            val decoded = codepointToString(char.code)
            assertEquals(char.toString(), decoded)
        }
    }

    @Test
    fun escape2() {
        // https://www.fileformat.info/info/unicode/char/0371/index.htm
        val char = '\u0371'
        val decoded = codepointToString(char.code)
        assertEquals(char.toString(), decoded)
    }

    @Test
    fun escape3() {
        // https://www.fileformat.info/info/unicode/char/2654/index.htm
        val char = '\u2654'
        val decoded = codepointToString(char.code)
        assertEquals(char.toString(), decoded)
    }

    @Test
    fun escape4() {
        // https://www.fileformat.info/info/unicode/char/2f804/index.htm
        val text = "\uD87E\uDC04"
        val decoded = codepointToString(0x2f804)
        assertEquals(text, decoded)
    }

}
