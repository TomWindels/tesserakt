
import dev.tesserakt.rdf.serialization.common.FileDataSource
import dev.tesserakt.rdf.serialization.common.FileStreamDataSource
import dev.tesserakt.rdf.serialization.turtle.Turtle
import dev.tesserakt.rdf.types.Store
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class TurtleStreamTest {

    @Test
    fun stream() = runTest {
        val filepath = "${js("process.cwd()")}/../../../../serialization/turtle/src/jvmTest/resources/turtle/railway-batch-1-inferred.ttl"
        val asyncSource = FileStreamDataSource(filepath)
        val asyncStore = Store(asyncSource, Turtle)
        println("Stream: read ${asyncStore.size} quad(s)!")
        val syncSource = FileDataSource(filepath)
        val syncStore = Store(syncSource, Turtle)
        println("Sync: read ${syncStore.size} quad(s)!")
        assertEquals(syncStore, asyncStore)
    }

}
