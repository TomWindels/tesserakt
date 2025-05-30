
import dev.tesserakt.benchmarking.RunnerConfig
import dev.tesserakt.benchmarking.RunnerEvaluation.Companion.toEvaluations
import dev.tesserakt.util.printerrln
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.minutes

suspend fun main(args: Array<String>) {
    val runtime = Runtime.getRuntime()
    val configs = RunnerConfig.fromCommandLine(args).toEvaluations()
    configs.forEachIndexed { i, config ->
        try {
            withTimeout(5.minutes) { config.createRunner().run() }
        } catch (t: TimeoutCancellationException) {
            printerrln("Timeout expired!")
        }
        // GC'ing in-between iterations, giving a fresh start for the warm up
        runtime.gc()
    }
    println("Execution finished!")
}
