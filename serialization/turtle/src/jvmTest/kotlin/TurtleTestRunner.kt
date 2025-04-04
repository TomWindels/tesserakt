
import dev.tesserakt.rdf.serialization.Turtle
import dev.tesserakt.rdf.serialization.util.BufferedString
import dev.tesserakt.rdf.serialization.util.openAsBufferedReader
import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.testing.testEnv
import dev.tesserakt.util.toTruncatedString
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

class TurtleTestRunner {

    data class TurtleTest(
        val filepath: String
    ): dev.tesserakt.testing.Test {

        data class Result(
            val obtained: List<Quad>,
            val expected: List<Quad>
        ): dev.tesserakt.testing.Test.Result {

            private val superfluous: List<Quad> = (obtained - expected)
                .sortedBy { it.s.hashCode() }
            private val missing: List<Quad> = (expected - obtained)
                .sortedBy { it.s.hashCode() }

            override fun isSuccess(): Boolean {
                return superfluous.isEmpty() && missing.isEmpty()
            }

            override fun exceptionOrNull(): Throwable? {
                return if (!isSuccess()) {
                    AssertionError("Received results do not match expectations!\n$this\n * The following ${superfluous.size} quad(s) are superfluous:\n\t${superfluous.toTruncatedString(500)}\n * The following ${missing.size} quad(s) are missing:\n\t${missing.toTruncatedString(500)}\n")
                } else null
            }

            override fun toString(): String {
                return " * Got ${obtained.size} quad(s):\n\t${obtained.toTruncatedString(500)}\n * Expected ${expected.size} quad(s):\n\t${expected.toTruncatedString(500)}"
            }
        }

        override suspend fun test(): Result {
            val a = Turtle.Parser(BufferedString(filepath.openAsBufferedReader().getOrThrow())).toList()
            val b = externalTurtleParser(filepath)
            return Result(obtained = a, expected = b)
        }

    }

    @Test
    fun deserialization() {
        val env = testEnv {
            listFiles("src/commonTest/resources/turtle")
                .forEach { add(TurtleTest(it)) }
        }
        runBlocking {
            env.run().report()
        }
    }

}
