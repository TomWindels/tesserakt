
import dev.tesserakt.concurrent.ConcurrencyMode
import dev.tesserakt.concurrent.MultiThreaded
import dev.tesserakt.concurrent.SingleThreaded
import dev.tesserakt.concurrent.set
import dev.tesserakt.rdf.serialization.turtle.Turtle
import dev.tesserakt.rdf.types.Store
import java.io.File
import kotlin.time.measureTime

fun main() {

    val path = System.getenv("INPUT_FILE")
        ?: throw IllegalStateException("No filepath provided (`INPUT_FILE` env var)")

    repeat(3) {
        ConcurrencyMode.set(SingleThreaded)

        run {
            println("=== 0 ===")
            val store: Store
            val t = measureTime {
                store = Store(File(path), Turtle)
            }
            println(store.size)
            println(" in $t")
        }

        ConcurrencyMode.set(MultiThreaded)
        run {
            println("=== 1 ===")
            val store: Store
            val t = measureTime {
                store = Store(File(path), Turtle)
            }
            println(store.size)
            println(" in $t")
        }
    }
}
