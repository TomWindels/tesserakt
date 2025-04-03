
import dev.tesserakt.benchmarking.RunnerConfig
import dev.tesserakt.util.printerrln
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.minutes

suspend fun main(args: Array<String>) {
    val configs = RunnerConfig.fromCommandLine(args.drop(2).toTypedArray())
    configs.forEachIndexed { i, config ->
        println("Evaluating benchmark ${i + 1}/${configs.size}")
        println(config)
        try {
            withTimeout(5.minutes) { config.createRunner().run() }
        } catch (t: TimeoutCancellationException) {
            printerrln("Timeout expired!")
        }
        // GC'ing in-between iterations, giving a fresh start for the warm up
//        runtime.gc()
    }
    println("Execution finished!")
}
